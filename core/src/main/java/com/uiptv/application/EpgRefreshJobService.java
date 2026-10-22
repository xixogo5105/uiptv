package com.uiptv.application;

import com.uiptv.db.ChannelDb;
import com.uiptv.db.EpgCacheDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.AppLog;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A cancellable, source-coalesced manual EPG refresh that keeps last-good data. */
public final class EpgRefreshJobService {
    public record SourceProgress(String id, String name, String status, String detail) { }
    public record Snapshot(String id, String status, String stage, int completed, int total,
                           String currentSource, String message, List<SourceProgress> sources,
                           int preparationCompleted, int preparationTotal, String currentAccount,
                           long startedAt, long finishedAt) { }

    private record MappingContext(Account account, List<String> sources) { }
    private static final class SourceWork {
        final String id;
        final String source;
        final String name;
        final LinkedHashMap<String, MappingContext> mappings = new LinkedHashMap<>();
        SourceWork(String id, String source, String name) { this.id = id; this.source = source; this.name = name; }
    }
    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final long startedAt = System.currentTimeMillis();
        final LinkedHashMap<String, SourceProgress> sources = new LinkedHashMap<>();
        volatile String status = "PREPARING";
        volatile String stage = "Preparing";
        volatile int completed;
        volatile int total = -1;
        volatile int preparationCompleted;
        volatile int preparationTotal = -1;
        volatile String currentSource = "";
        volatile String currentAccount = "";
        volatile String message = "";
        volatile long finishedAt;
        volatile Thread worker;

        synchronized void stage(String value) {
            stage = value;
        }
        synchronized void waitingForRefresh() {
            stage("Waiting for another EPG refresh to stop");
            message = "The current refresh is stopping so this one can begin.";
        }
        synchronized void beginPreparation(int accounts) {
            preparationCompleted = 0;
            preparationTotal = accounts;
            stage("Reading accounts");
            currentAccount = "";
            message = "";
        }
        synchronized void checkingAccount(Account account) {
            stage("Checking account sources");
            currentAccount = Objects.toString(account.getAccountName(), "");
            message = "Checking configured guide sources for " + currentAccount + ".";
        }
        synchronized void accountPrepared() { preparationCompleted++; }
        synchronized void checkingGlobalSources() {
            stage("Checking global sources");
            currentAccount = "";
            message = "";
        }
        synchronized void prepare(Collection<SourceWork> work) {
            total = work.size();
            work.forEach(item -> sources.put(item.id, new SourceProgress(item.id, item.name, "Pending", "")));
            status = "RUNNING";
            stage(work.isEmpty() ? "Completed" : "Ready");
            currentAccount = "";
            message = work.isEmpty() ? "No EPG sources are configured." : "";
        }
        synchronized void update(SourceWork source, String state, String detail) {
            currentSource = source.name;
            currentAccount = "";
            sources.put(source.id, new SourceProgress(source.id, source.name, state, Objects.toString(detail, "")));
            stage(state);
        }
        synchronized void sourceFinished(SourceWork source, boolean succeeded, String detail) {
            sources.put(source.id, new SourceProgress(source.id, source.name,
                    succeeded ? "Completed" : "Failed", Objects.toString(detail, "")));
            completed++;
            stage(succeeded ? "Saved" : "Source unavailable");
        }
        synchronized Snapshot snapshot() {
            return new Snapshot(id, status, stage, completed, total, currentSource, message,
                    List.copyOf(sources.values()), preparationCompleted, preparationTotal, currentAccount,
                    startedAt, finishedAt);
        }
        synchronized void finish(String result, String detail) {
            status = result;
            stage(result.equals("COMPLETED") || result.equals("COMPLETED_WITH_WARNINGS") ? "Completed" : result);
            message = Objects.toString(detail, "");
            finishedAt = System.currentTimeMillis();
        }
        boolean terminal() { return finishedAt != 0; }
    }

    private static final Object JOBS_LOCK = new Object();
    private static final LinkedHashMap<String, Job> JOBS = new LinkedHashMap<>();
    private static final AtomicReference<Job> ACTIVE = new AtomicReference<>();

    private EpgRefreshJobService() { }

    /** Starts one refresh. A second caller joins the already-running job. */
    public static Snapshot start() {
        synchronized (JOBS_LOCK) {
            Job active = ACTIVE.get();
            if (active != null && !active.terminal()) return active.snapshot();
            Job job = new Job();
            JOBS.put(job.id, job);
            while (JOBS.size() > 20) JOBS.remove(JOBS.keySet().iterator().next());
            ACTIVE.set(job);
            EpgCacheRefreshService.prioritizeManualRefresh();
            Thread worker = Thread.ofVirtual().name("epg-manual-refresh-" + job.id).unstarted(() -> run(job));
            job.worker = worker;
            worker.start();
            return job.snapshot();
        }
    }

    public static Snapshot get(String id) {
        synchronized (JOBS_LOCK) {
            Job job = JOBS.get(id);
            return job == null ? null : job.snapshot();
        }
    }

    public static boolean cancel(String id) {
        synchronized (JOBS_LOCK) {
            Job job = JOBS.get(id);
            if (job == null || job.terminal()) return false;
            job.message = "Stop requested. The last saved guide data is being kept.";
            Thread worker = job.worker;
            if (worker != null) worker.interrupt();
            return true;
        }
    }

    private static void run(Job job) {
        Thread worker = Thread.currentThread();
        EpgCacheRefreshService.WORKERS.add(worker);
        boolean locked = false;
        try {
            job.waitingForRefresh();
            while (!EpgCacheRefreshService.REFRESH_LOCK.tryLock(1, TimeUnit.SECONDS)) {
                // A scheduled import can still be in database I/O; keep asking it to yield.
                EpgCacheRefreshService.prioritizeManualRefresh();
            }
            locked = true;
            job.stage("Preparing");
            EpgCacheRefreshService.Token token = EpgCacheRefreshService.token();
            EpgApplicationService epg = new EpgApplicationService();
            if (!epg.isEnabled()) {
                job.finish("SKIPPED", "Enable EPG before refreshing guide sources.");
                return;
            }

            Map<String, SourceWork> sources = prepareSources(job, epg, token);
            if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("EPG settings changed");
            job.prepare(sources.values());
            int failures = 0;
            for (SourceWork source : sources.values()) {
                if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                try {
                    XmltvGuide guide = epg.downloadSource(source.source,
                            phase -> job.update(source, phase,
                                    "Downloading".equals(phase) ? "Connecting to guide source." : "Reading guide data."),
                            bytes -> job.update(source, "Parsing", "Read " + readableBytes(bytes) + " from guide."));
                    if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                    job.update(source, "Matching", "Applying configured account and guide identities.");
                    List<EpgCacheDb.Match> mappings = new ArrayList<>();
                    for (MappingContext context : source.mappings.values()) {
                        if (context.account().getDbId() == null) continue;
                        job.update(source, "Loading cached channels",
                                Objects.toString(context.account().getAccountName(), ""));
                        List<Channel> channels = ChannelDb.get().getCachedLiveChannels(context.account().getDbId());
                        if (!channels.isEmpty()) mappings.addAll(EpgCacheApplicationService.matches(
                                context.account(), channels, context.sources()));
                    }
                    long now = System.currentTimeMillis();
                    long retryAt = now + EpgCacheRefreshService.refreshMillis();
                    java.util.concurrent.atomic.AtomicLong programmes = new java.util.concurrent.atomic.AtomicLong();
                    job.update(source, "Saving", "Processing guide programmes.");
                    EpgCacheRefreshService.commit(token, () -> {
                        new EpgCacheDb().replaceSource(source.id, guide.programmes(), guide.names(),
                                guide.qualityNames(), guide.icons(), now, retryAt, Set.of(),
                                scanned -> {
                                    programmes.set(scanned);
                                    job.update(source, "Saving", "Processed " + scanned + " programmes.");
                                });
                        if (!mappings.isEmpty()) {
                            job.update(source, "Linking channels", "Matching " + mappings.size() + " cached channels.");
                            new EpgCacheDb().mapChannels(mappings, now, false,
                                    scanned -> job.update(source, "Linking channels",
                                            "Matched " + scanned + " cached channels."));
                        }
                    });
                    if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                    job.sourceFinished(source, true, "Imported " + programmes.get() + " programmes.");
                } catch (InterruptedException cancelled) {
                    throw cancelled;
                } catch (Exception error) {
                    failures++;
                    String warning = EpgApplicationService.warning(error);
                    try {
                        EpgCacheRefreshService.commit(token, () -> new EpgCacheDb().failed(source.id,
                                System.currentTimeMillis() + java.util.concurrent.TimeUnit.MINUTES.toMillis(5), warning));
                    } catch (RuntimeException cacheError) {
                        error.addSuppressed(cacheError);
                    }
                    AppLog.addWarningLog(EpgRefreshJobService.class,
                            "Manual EPG source refresh failed; retaining cached programmes (" + warning + ").");
                    job.sourceFinished(source, false, warning);
                }
            }
            if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
            job.finish(failures == 0 ? "COMPLETED" : "COMPLETED_WITH_WARNINGS",
                    failures == 0 ? "All configured XMLTV sources were refreshed."
                            : failures + " source(s) failed. Existing guide data was retained for those sources.");
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            job.finish("CANCELLED", "Refresh stopped. Completed source imports are kept; uncompleted sources retain their previous data.");
        } catch (RuntimeException error) {
            AppLog.addWarningLog(EpgRefreshJobService.class, "Manual EPG refresh could not be prepared; existing guide data remains available.");
            job.finish("FAILED", "Unable to prepare EPG sources. Existing guide data remains available.");
        } finally {
            if (locked) EpgCacheRefreshService.REFRESH_LOCK.unlock();
            EpgCacheRefreshService.WORKERS.remove(worker);
            Thread.interrupted();
            if (job.finishedAt == 0) job.finish("FAILED", "Refresh ended before completion.");
            ACTIVE.compareAndSet(job, null);
        }
    }

    private static Map<String, SourceWork> prepareSources(Job job, EpgApplicationService epg,
                                                          EpgCacheRefreshService.Token token) {
        job.stage("Reading accounts");
        Map<String, SourceWork> result = new LinkedHashMap<>();
        List<Account> accounts = new ArrayList<>(AccountService.getInstance().getAllById().values());
        accounts.sort(Comparator.comparing(account -> Objects.toString(account.getAccountName(), ""), String.CASE_INSENSITIVE_ORDER));
        job.beginPreparation(accounts.size());
        for (Account account : accounts) {
            if (!EpgCacheRefreshService.current(token)) break;
            job.checkingAccount(account);
            account.setAction(Account.AccountAction.itv);
            List<String> configured = epg.cacheSources(account);
            MappingContext context = new MappingContext(account, List.copyOf(configured));
            for (String source : configured) {
                String id = EpgCacheApplicationService.sourceId(source);
                SourceWork work = result.computeIfAbsent(id, _ -> new SourceWork(id, source, sourceLabel(source)));
                if (account.getDbId() != null) work.mappings.putIfAbsent(account.getDbId(), context);
            }
            job.accountPrepared();
        }
        job.checkingGlobalSources();
        for (String source : EpgApplicationService.globalSources(
                ConfigurationService.getInstance().read().getGlobalEpgSource())) {
            String id = EpgCacheApplicationService.sourceId(source);
            result.computeIfAbsent(id, _ -> new SourceWork(id, source, sourceLabel(source)));
        }
        job.stage("Preparing sources");
        return result;
    }

    private static String readableBytes(long bytes) {
        if (bytes < 1_048_576) return (bytes / 1_024) + " KB";
        return "%.1f MB".formatted(bytes / 1_048_576.0);
    }

    private static String sourceLabel(String source) {
        try {
            URI uri = URI.create(source);
            if (uri.getHost() != null) return uri.getHost() + Objects.toString(uri.getPath(), "");
            if ("file".equalsIgnoreCase(uri.getScheme())) return Path.of(uri).getFileName().toString();
        } catch (RuntimeException ignored) { }
        try { return Path.of(source).getFileName().toString(); }
        catch (RuntimeException ignored) { return "XMLTV source"; }
    }
}
