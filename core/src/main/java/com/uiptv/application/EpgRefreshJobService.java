package com.uiptv.application;

import com.uiptv.db.ChannelDb;
import com.uiptv.db.EpgCacheDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A cancellable, source-coalesced manual EPG refresh that keeps last-good data. */
public final class EpgRefreshJobService {
    public record SourceProgress(String id, String source, String name, String fileName, String status, String detail,
                                 long programmes, int matchedChannels) {
        public SourceProgress(String id, String name, String fileName, String status, String detail,
                              long programmes, int matchedChannels) {
            this(id, "", name, fileName, status, detail, programmes, matchedChannels);
        }
    }
    public record Snapshot(String id, String status, String stage, int completed, int total,
                           String currentSource, String message, List<SourceProgress> sources,
                           int preparationCompleted, int preparationTotal, String currentAccount,
                           long startedAt, long finishedAt) { }

    private record MappingContext(Account account, List<String> sources) { }
    private static final class SourceWork {
        final String id;
        final String source;
        final String name;
        final String fileName;
        boolean global;
        final LinkedHashMap<String, MappingContext> mappings = new LinkedHashMap<>();
        SourceWork(String id, String source, String name, String fileName) {
            this.id = id;
            this.source = source;
            this.name = name;
            this.fileName = fileName;
        }
    }
    private record SuccessfulSource(SourceWork source, long programmes) { }
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
        int warnings;

        synchronized void stage(String value) {
            stage = value;
        }
        synchronized void waitingForRefresh() {
            stage("Waiting for another EPG refresh to stop");
            message = "";
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
            message = "";
        }
        synchronized void accountPrepared() { preparationCompleted++; }
        synchronized void checkingGlobalSources() {
            stage("Checking global sources");
            currentAccount = "";
            message = "";
        }
        synchronized void prepare(Collection<SourceWork> work) {
            total = work.size();
            work.forEach(item -> sources.put(item.id,
                    new SourceProgress(item.id, item.source, item.name, item.fileName, "Pending", "", 0, 0)));
            status = "RUNNING";
            stage(work.isEmpty() ? "No sources configured" : "Ready");
            currentAccount = "";
            message = work.isEmpty() ? I18n.tr("epgRefreshNoSourcesMessage") : "";
        }
        synchronized void update(SourceWork source, String state, String detail) {
            currentSource = sourceDisplayName(source);
            currentAccount = "";
            SourceProgress previous = sources.get(source.id);
            sources.put(source.id, new SourceProgress(source.id, source.source, source.name, source.fileName, state,
                    Objects.toString(detail, ""), previous == null ? 0 : previous.programmes(),
                    previous == null ? 0 : previous.matchedChannels()));
            stage(state);
        }
        synchronized void sourceFinished(SourceWork source, String result, String detail,
                                         long programmes, int matchedChannels) {
            sources.put(source.id, new SourceProgress(source.id, source.source, source.name, source.fileName, result,
                    Objects.toString(detail, ""), programmes, matchedChannels));
            completed++;
            if ("No matches".equals(result) || "Failed".equals(result)) warnings++;
            stage("Failed".equals(result) ? "Source unavailable" : "Saved");
        }
        synchronized void loadingMappings(Account account) {
            stage("Loading cached channels");
            currentAccount = Objects.toString(account.getAccountName(), "");
            message = "";
        }
        synchronized void sourceImported(SourceWork source, long programmes) {
            sources.put(source.id, new SourceProgress(source.id, source.source, source.name, source.fileName,
                    "Saved", I18n.tr("epgRefreshProcessedProgrammesDetail", programmes), programmes, 0));
            stage("Saved");
        }
        synchronized Snapshot snapshot() {
            return new Snapshot(id, status, stage, completed, total, currentSource, message,
                    List.copyOf(sources.values()), preparationCompleted, preparationTotal, currentAccount,
                    startedAt, finishedAt);
        }
        synchronized void finish(String result, String detail) {
            status = result;
            stage(switch (result) {
                case "COMPLETED" -> "Completed";
                case "COMPLETED_WITH_WARNINGS" -> "Warnings";
                case "NO_SOURCES" -> "No sources configured";
                case "CANCELLED" -> "Cancelled";
                case "SKIPPED" -> "Skipped";
                case "FAILED" -> "Failed";
                default -> result;
            });
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
            job.message = I18n.tr("epgRefreshStopRequestedMessage");
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
                job.finish("SKIPPED", I18n.tr("epgRefreshEnableFirstMessage"));
                return;
            }

            Map<String, SourceWork> sources = prepareSources(job, epg, token);
            if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("EPG settings changed");
            job.prepare(sources.values());
            if (sources.isEmpty()) {
                job.finish("NO_SOURCES", I18n.tr("epgRefreshNoSourcesMessage"));
                return;
            }
            int failures = 0;
            Map<String, List<EpgCacheDb.Match>> mappingsByAccount = prepareMappings(job, sources.values(), token);
            List<EpgCacheDb.Match> mappings = mappingsByAccount.values().stream()
                    .flatMap(Collection::stream).toList();
            List<SuccessfulSource> imported = new ArrayList<>();
            try (var staged = new EpgCacheDb().stageSources()) {
                for (SourceWork source : sources.values()) {
                    if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                    try {
                        try (XmltvGuide guide = epg.downloadSource(source.source,
                                phase -> job.update(source, phase, ""),
                                bytes -> job.update(source, "Parsing", I18n.tr("epgRefreshReadBytesDetail", readableBytes(bytes))))) {
                        if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                        long now = System.currentTimeMillis();
                        long interval = source.global ? EpgCacheRefreshService.globalRefreshMillis()
                                : EpgCacheRefreshService.refreshMillis();
                        long retryAt = now + interval;
                        java.util.concurrent.atomic.AtomicLong programmes = new java.util.concurrent.atomic.AtomicLong();
                        job.update(source, "Staging", "");
                        EpgCacheRefreshService.commit(token, () -> {
                            staged.add(source.id, guide, now, retryAt,
                                    scanned -> {
                                        programmes.set(scanned);
                                        job.update(source, "Staging", I18n.tr("epgRefreshProcessedProgrammesDetail", scanned));
                                    });
                        });
                        if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                        EpgCacheRefreshService.commit(token, () -> staged.publishSourceData(source.id));
                        if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                        imported.add(new SuccessfulSource(source, programmes.get()));
                        job.sourceImported(source, programmes.get());
                        }
                    } catch (InterruptedException cancelled) {
                        throw cancelled;
                    } catch (Exception error) {
                        failures++;
                        String warning = EpgApplicationService.warning(error);
                        try {
                            long retryDelay = source.global ? EpgCacheRefreshService.globalRefreshMillis()
                                    : java.util.concurrent.TimeUnit.MINUTES.toMillis(5);
                            EpgCacheRefreshService.commit(token, () -> new EpgCacheDb().failed(source.id,
                                    System.currentTimeMillis() + retryDelay, warning));
                        } catch (RuntimeException cacheError) {
                            error.addSuppressed(cacheError);
                        }
                        AppLog.addWarningLog(EpgRefreshJobService.class,
                                "Manual EPG source refresh failed; retaining cached programmes (" + warning + ").");
                        job.sourceFinished(source, "Failed", warning, 0, 0);
                    }
                }
                if (!imported.isEmpty()) {
                    SourceWork progressSource = imported.getFirst().source();
                    job.update(progressSource, "Linking channels",
                            I18n.tr("epgRefreshMatchingChannelsDetail", mappings.size()));
                    try {
                        java.util.concurrent.atomic.AtomicReference<EpgCacheDb.MappingReport> report =
                                new java.util.concurrent.atomic.AtomicReference<>();
                        job.update(progressSource, "Publishing", "");
                        EpgCacheRefreshService.commit(token, () -> report.set(staged.publishMappings(
                                mappings, System.currentTimeMillis(),
                                scanned -> job.update(progressSource, "Publishing",
                                        I18n.tr("epgRefreshMatchedChannelsDetail", scanned)))));
                        if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                        EpgCacheDb.MappingReport mapping = report.get();
                        for (SuccessfulSource success : imported) {
                            int matched = mapping == null ? 0
                                    : mapping.mappedBySource().getOrDefault(success.source().id, 0);
                            if (matched == 0) {
                                String detail = success.programmes() == 0
                                        ? I18n.tr("epgRefreshNoCurrentProgrammesDetail")
                                        : I18n.tr("epgRefreshNoMatchesDetail", success.programmes());
                                job.sourceFinished(success.source(), "No matches",
                                        detail, success.programmes(), 0);
                            } else {
                                job.sourceFinished(success.source(), "Completed",
                                        I18n.tr("epgRefreshImportedDetail", success.programmes(), matched),
                                        success.programmes(), matched);
                            }
                        }
                    } catch (RuntimeException error) {
                        if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
                        String warning = EpgApplicationService.warning(error);
                        failures++;
                        job.warnings++;
                        for (SuccessfulSource success : imported) {
                            job.sourceFinished(success.source(), "Saved", warning,
                                    success.programmes(), 0);
                        }
                        AppLog.addWarningLog(EpgRefreshJobService.class,
                                "EPG channel matching failed after source data was saved (" + warning + ").");
                    }
                }
            }
            if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
            int noMatches = job.warnings - failures;
            boolean hasWarnings = failures > 0 || noMatches > 0;
            String detail = hasWarnings
                    ? I18n.tr("epgRefreshWarningsMessage", failures, noMatches)
                    : I18n.tr("epgRefreshCompleteMessage");
            job.finish(hasWarnings ? "COMPLETED_WITH_WARNINGS" : "COMPLETED", detail);
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            job.finish("CANCELLED", I18n.tr("epgRefreshCancelledMessage"));
        } catch (RuntimeException error) {
            AppLog.addWarningLog(EpgRefreshJobService.class, "Manual EPG refresh could not be prepared; existing guide data remains available.");
            job.finish("FAILED", I18n.tr("epgRefreshFailedMessage"));
        } finally {
            if (locked) EpgCacheRefreshService.REFRESH_LOCK.unlock();
            EpgCacheRefreshService.WORKERS.remove(worker);
            Thread.interrupted();
            if (job.finishedAt == 0) job.finish("FAILED", I18n.tr("epgRefreshFailedMessage"));
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
                SourceWork work = result.computeIfAbsent(id, _ -> new SourceWork(id, source,
                        sourceLabel(source), sourceFileName(source)));
                if (account.getDbId() != null) work.mappings.putIfAbsent(account.getDbId(), context);
            }
            job.accountPrepared();
        }
        job.checkingGlobalSources();
        for (String source : EpgSourceBlacklistService.filter(EpgApplicationService.globalSources(
                ConfigurationService.getInstance().read().getGlobalEpgSource()))) {
            String id = EpgCacheApplicationService.sourceId(source);
            SourceWork work = result.computeIfAbsent(id, _ -> new SourceWork(id, source,
                    sourceLabel(source), sourceFileName(source)));
            work.global = true;
        }
        job.stage("Preparing sources");
        return result;
    }

    /** Build each account's channel match list once for the whole refresh. */
    private static Map<String, List<EpgCacheDb.Match>> prepareMappings(Job job,
                                                                        Collection<SourceWork> works,
                                                                        EpgCacheRefreshService.Token token)
            throws InterruptedException {
        LinkedHashMap<String, MappingContext> accounts = new LinkedHashMap<>();
        for (SourceWork work : works) {
            for (MappingContext context : work.mappings.values()) {
                if (context.account().getDbId() != null) accounts.putIfAbsent(context.account().getDbId(), context);
            }
        }
        Map<String, List<EpgCacheDb.Match>> mappings = new LinkedHashMap<>();
        for (MappingContext context : accounts.values()) {
            if (!EpgCacheRefreshService.current(token)) throw new InterruptedException("Refresh cancelled");
            job.loadingMappings(context.account());
            List<Channel> channels = ChannelDb.get().getCachedLiveChannels(context.account().getDbId());
            mappings.put(context.account().getDbId(), channels.isEmpty()
                    ? List.of()
                    : EpgCacheApplicationService.matches(context.account(), channels, context.sources()));
        }
        return mappings;
    }

    private static String readableBytes(long bytes) {
        if (bytes < 1_048_576) return (bytes / 1_024) + " KB";
        return "%.1f MB".formatted(bytes / 1_048_576.0);
    }

    static String sourceLabel(String source) {
        try {
            URI uri = URI.create(source);
            if (uri.getHost() != null) return uri.getHost();
            if ("file".equalsIgnoreCase(uri.getScheme())) return I18n.tr("epgSourceLocalFile");
        } catch (RuntimeException ignored) { }
        return I18n.tr("epgSourceLocalFile");
    }

    static String sourceFileName(String source) {
        if (source == null || source.isBlank()) return "";
        String value = source.strip();
        try {
            URI uri = URI.create(value.replace(" ", "%20"));
            String path = uri.getScheme() == null ? value : uri.getPath();
            if (path == null || path.isBlank()) return "";
            Path fileName = Path.of(path).getFileName();
            return fileName == null ? "" : fileName.toString();
        } catch (RuntimeException ignored) {
            if (value.contains("://")) return "";
            try {
                Path fileName = Path.of(value).getFileName();
                return fileName == null ? "" : fileName.toString();
            } catch (RuntimeException ignoredPath) {
                return "";
            }
        }
    }

    private static String sourceDisplayName(SourceWork source) {
        return source.fileName.isBlank() ? source.name : source.name + " · " + source.fileName;
    }
}
