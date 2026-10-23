package com.uiptv.application;

import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AppLog;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** One bounded background importer, independent of card rendering. */
public class EpgCacheRefreshService {
    static long refreshMillis() {
        return TimeUnit.HOURS.toMillis(ConfigurationService.getInstance().read().normalizedEpgRefreshHours());
    }
    private static long schedulerIntervalMillis() {
        return TimeUnit.MINUTES.toMillis(ConfigurationService.getInstance().read().normalizedEpgSchedulerIntervalMinutes());
    }
    private static final long FAILURE_MS=TimeUnit.MINUTES.toMillis(5);
    private static final Object COMMIT_LOCK=new Object();
    /** Serializes scheduled and user-requested imports without discarding persisted data. */
    static final java.util.concurrent.locks.ReentrantLock REFRESH_LOCK = new java.util.concurrent.locks.ReentrantLock();
    private static final AtomicReference<Thread> SCHEDULED_WORKER = new AtomicReference<>();
    private static final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.locks.ReentrantLock> NATIVE_REQUEST_LOCKS = new ConcurrentHashMap<>();
    private static final AtomicLong EPOCH=new AtomicLong();
    private static final Map<Runnable,EpgChangeNotifications.Subscription> LEGACY_LISTENERS=new ConcurrentHashMap<>();
    private static ScheduledExecutorService scheduler;
    private static boolean applicationOwned;
    /** The last state observed while this lifecycle was running. */
    private static volatile boolean backgroundRefreshWasEnabled;
    public static synchronized void startApplication() { applicationOwned=true; start(); }
    public static void stopServer() { stop(true); }
    /**
     * Final JVM shutdown path: cancel EPG work without waiting for a lifecycle
     * read that may be blocked behind provider/database teardown.
     */
    public static void stopForApplicationExit() { stop(false, false); }
    private static final ConfigurationChangeListener CONFIG_CHANGED=_ -> settingsChanged();
    private static final AccountChangeListener ACCOUNTS_CHANGED=_ -> settingsChanged();
    private static void settingsChanged() {
        boolean wasEnabled = backgroundRefreshWasEnabled;
        EPOCH.incrementAndGet();
        WORKERS.forEach(Thread::interrupt);
        stopTimer();
        boolean enabled = isEnabled();
        backgroundRefreshWasEnabled = enabled;
        // A save which turns full EPG back on must populate the guide now. Once
        // that run completes, scheduleWithFixedDelay applies the configured
        // interval to all subsequent checks.
        if (enabled && !wasEnabled) IMMEDIATE_REQUESTED.set(true);
        if (enabled) activateIfEnabled();
    }
    private static boolean isEnabled() {
        try { return new EpgApplicationService().isBackgroundRefreshEnabled(); }
        catch (RuntimeException _) {
            if (lifecycleStarted) AppLog.addWarningLog(EpgCacheRefreshService.class, "Unable to read EPG refresh settings; guide refresh remains stopped.");
            return false;
        }
    }
    static final Set<Thread> WORKERS=ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean QUEUED=new AtomicBoolean();
    private static final AtomicBoolean IMMEDIATE_REQUESTED=new AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicInteger CATALOGUE_RELOADS = new java.util.concurrent.atomic.AtomicInteger();
    public static CataloguePause pauseForCatalogueReload() {
        CATALOGUE_RELOADS.incrementAndGet();
        WORKERS.forEach(Thread::interrupt);
        return new CataloguePause();
    }
    public static final class CataloguePause implements AutoCloseable {
        private boolean closed;
        @Override public void close() { if (!closed) { closed = true; CATALOGUE_RELOADS.decrementAndGet(); } }
    }
    private record VisibleChannel(String accountId, String providerId) { }
    private static final Map<VisibleChannel,Channel> VISIBLE = new LinkedHashMap<>();
    /** Queue visible channels for the lifecycle-owned importer; reads never fetch providers. */
    static void requestVisible(Account account, List<Channel> channels) {
        if (!lifecycleStarted || !new EpgApplicationService().isBackgroundRefreshEnabled()
                || account.getDbId() == null || channels.isEmpty()) return;
        synchronized (VISIBLE) {
            for (Channel channel : channels) {
                if (channel.getChannelId() == null || channel.getChannelId().isBlank()) continue;
                VISIBLE.put(new VisibleChannel(account.getDbId(), channel.getChannelId()), channel);
                while (VISIBLE.size() > 256) VISIBLE.remove(VISIBLE.keySet().iterator().next());
            }
        }
        requestRefresh();
    }
    private void refreshVisible(Token token) {
        Map<String,List<Channel>> pending = new LinkedHashMap<>();
        synchronized (VISIBLE) {
            if (!current(token)) return;
            VISIBLE.forEach((key,channel) -> pending.computeIfAbsent(key.accountId(), _ -> new ArrayList<>()).add(channel));
            VISIBLE.clear();
        }
        for (var entry : pending.entrySet()) {
            if (!current(token)) return;
            Account account = AccountService.getInstance().getById(entry.getKey());
            if (account == null) continue;
            account.setAction(Account.AccountAction.itv);
            // Visible catalogue channels may publish already cached XMLTV data, but a
            // background visibility update must never fan out Stalker channel requests.
            refreshAccount(account, entry.getValue(), token, entry.getValue().size(), List.of());
        }
    }
    private static final Map<String,Integer> CURSORS=new HashMap<>();
    record Token(String path,long epoch) { }
    static Token token() { return new Token(SQLConnection.getDatabasePath(),EPOCH.get()); }
    static boolean current(Token token) { return CATALOGUE_RELOADS.get() == 0 && token.epoch()==EPOCH.get() && token.path().equals(SQLConnection.getDatabasePath()) && !Thread.currentThread().isInterrupted(); }
    static void commit(Token token,Runnable write) {
        synchronized(COMMIT_LOCK) { if(current(token)) write.run(); }
    }
    public static long revision() { return EpgChangeNotifications.snapshot().revision(); }
    public static void addListener(Runnable listener) {
        LEGACY_LISTENERS.computeIfAbsent(listener, key -> EpgChangeNotifications.subscribe(revision(), _ -> key.run()));
    }
    public static void removeListener(Runnable listener) {
        var subscription = LEGACY_LISTENERS.remove(listener);
        if (subscription != null) subscription.close();
    }

    private static volatile boolean lifecycleStarted;
    private static final AtomicReference<Thread> RECONCILER=new AtomicReference<>();
    public static synchronized void start() {
        if(!lifecycleStarted) {
            lifecycleStarted=true;
            backgroundRefreshWasEnabled = isEnabled();
            ConfigurationService.getInstance().addChangeListener(CONFIG_CHANGED);
            AccountService.getInstance().addChangeListener(ACCOUNTS_CHANGED);
        }
        activateIfEnabled();
    }
    private static synchronized void activateIfEnabled() {
        if (!lifecycleStarted || !isEnabled()) return;
        reconcileSoon();
    }
    private static synchronized void reconcileSoon() {
        if (!lifecycleStarted || RECONCILER.get() != null) return;
        long epoch = EPOCH.get();
        Thread task = Thread.ofVirtual().name("epg-cache-lifecycle").unstarted(() -> {
            try {
                reconcile(epoch);
            } catch (DatabaseAccessException _) {
                // A cancelled lifecycle must not report teardown as a database failure.
                if (lifecycleStarted && epoch == EPOCH.get() && !Thread.currentThread().isInterrupted())
                    AppLog.addWarningLog(EpgCacheRefreshService.class,"Unable to read EPG refresh settings; existing guide remains available.");
            } finally {
                synchronized (EpgCacheRefreshService.class) {
                    RECONCILER.compareAndSet(Thread.currentThread(), null);
                    if (epoch != EPOCH.get() && lifecycleStarted) reconcileSoon();
                }
            }
        });
        RECONCILER.set(task);
        task.start();
    }

    private static void reconcile(long epoch) {
        if (stale(epoch)) return;
        boolean enabled = isEnabled();
        synchronized (EpgCacheRefreshService.class) {
            if (stale(epoch)) return;
            if (!enabled) stopTimer(); else startTimer();
        }
    }
    private static boolean stale(long epoch) {
        return !lifecycleStarted || epoch != EPOCH.get() || Thread.currentThread().isInterrupted();
    }
    private static void startTimer() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "epg-cache-refresh");
            thread.setDaemon(true);
            return thread;
        });
        // Visible cards still wake the scheduler immediately; the delay moves the broad sweep
        // out of application startup.
        long interval = schedulerIntervalMillis();
        long initialDelay = IMMEDIATE_REQUESTED.getAndSet(false) ? 0 : 15;
        scheduler.scheduleWithFixedDelay(EpgCacheRefreshService::runScheduled, initialDelay, interval,
                TimeUnit.MILLISECONDS);
    }
    private static synchronized void stopTimer() {
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
        QUEUED.set(false);
    }
    public static void stop() { stop(false, true); }
    private static void stop(boolean serverOnly) { stop(serverOnly, true); }
    private static void stop(boolean serverOnly, boolean awaitLifecycle) {
        Thread lifecycleTask;
        List<Thread> workers;
        synchronized (EpgCacheRefreshService.class) {
            if (serverOnly && applicationOwned) return;
            lifecycleStarted = false;
            backgroundRefreshWasEnabled = false;
            IMMEDIATE_REQUESTED.set(false);
            synchronized (VISIBLE) { VISIBLE.clear(); }
            EPOCH.incrementAndGet();
            ConfigurationService.getInstance().removeChangeListener(CONFIG_CHANGED);
            AccountService.getInstance().removeChangeListener(ACCOUNTS_CHANGED);
            lifecycleTask = RECONCILER.get();
            if (lifecycleTask != null) lifecycleTask.interrupt();
            workers = new ArrayList<>(WORKERS);
            workers.forEach(Thread::interrupt);
            stopTimer(); applicationOwned = false;
        }
        // Drain normal lifecycle shutdown so callers can safely replace the database or remove
        // a test database after stop() returns. Provider I/O is interrupted first and the bounded
        // wait below prevents shutdown from hanging indefinitely. The final JVM-exit path still
        // uses stopForApplicationExit(), which deliberately skips this wait.
        try {
            if (awaitLifecycle) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                if (lifecycleTask != null && lifecycleTask != Thread.currentThread())
                    joinUntil(lifecycleTask, deadline);
                for (Thread worker : workers) {
                    if (worker != Thread.currentThread()) joinUntil(worker, deadline);
                }
            }
        } catch (InterruptedException _) { Thread.currentThread().interrupt(); }
    }
    private static void joinUntil(Thread thread, long deadline) throws InterruptedException {
        long remaining = deadline - System.nanoTime();
        if (remaining > 0 && thread.isAlive()) thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
    }
    /** Wake a running scheduler, but never create an unmanaged worker from a read path. */
    public static synchronized void requestRefresh() {
        if (scheduler == null || scheduler.isShutdown()) {
            IMMEDIATE_REQUESTED.set(true);
            if (lifecycleStarted) reconcileSoon();
            return;
        }
        if(!QUEUED.compareAndSet(false,true)) return;
        scheduler.execute(() -> { QUEUED.set(false); runScheduled(); });
    }
    static void invalidate() {
        invalidate(_ -> { });
    }
    static void invalidate(java.util.function.Consumer<String> progress) {
        try (var pause = pauseForCatalogueReload()) {
            EPOCH.incrementAndGet();
            progress.accept("epgClearStopping");
            synchronized(COMMIT_LOCK) { new EpgCacheDb().clear(progress); }
        }
    }
    private static void runScheduled() {
        synchronized (EpgCacheRefreshService.class) {
            if (!lifecycleStarted || CATALOGUE_RELOADS.get() > 0 || Thread.currentThread().isInterrupted()
                    || !REFRESH_LOCK.tryLock()) return;
            SCHEDULED_WORKER.set(Thread.currentThread());
            WORKERS.add(Thread.currentThread());
        }
        try {
            EpgChangeNotifications.pollExternalChanges();
            if(!new EpgApplicationService().isEnabled()) return;
            new EpgCacheRefreshService().refreshDue();
        } catch(Exception _) {
            if(!Thread.currentThread().isInterrupted()) AppLog.addWarningLog(EpgCacheRefreshService.class,"EPG background refresh failed; existing guide remains available.");
        } finally {
            SCHEDULED_WORKER.compareAndSet(Thread.currentThread(), null);
            WORKERS.remove(Thread.currentThread());
            Thread.interrupted();
            REFRESH_LOCK.unlock();
        }
    }

    /** Let a user-requested import take the lock after the current background operation stops. */
    static void prioritizeManualRefresh() {
        Thread scheduled = SCHEDULED_WORKER.get();
        if (scheduled != null) scheduled.interrupt();
    }

    public void refreshDue() {
        if(!new EpgApplicationService().isEnabled()) return;
        Token token=token();
        refreshVisible(token);
        Map<String,List<Channel>> bookmarked=bookmarkedChannels();
        List<Account> accounts = new ArrayList<>(AccountService.getInstance().getAllById().values());
        accounts.sort(Comparator.comparing(account -> bookmarked.getOrDefault(account.getDbId(),List.of()).isEmpty()));
        refreshAccounts(accounts, bookmarked, token);
        commit(token,() -> new EpgCacheDb().prune(System.currentTimeMillis()));
    }

    private Map<String,List<Channel>> bookmarkedChannels() {
        Map<String,List<Channel>> bookmarked=new HashMap<>();
        for(var resolved:new BookmarkResolver().resolveBookmarks(BookmarkService.getInstance().read())) {
            if(resolved.getAccount()==null || resolved.getAccountAction()!=Account.AccountAction.itv) continue;
            Bookmark bookmark=resolved.getBookmark();
            Channel channel=Channel.fromJson(bookmark.getChannelJson());
            if(channel==null) channel=new Channel();
            channel.setChannelId(bookmark.getChannelId()); channel.setName(bookmark.getChannelName());
            bookmarked.computeIfAbsent(resolved.getAccount().getDbId(),_ -> new ArrayList<>()).add(channel);
        }
        return bookmarked;
    }

    private void refreshAccounts(List<Account> accounts, Map<String,List<Channel>> bookmarked, Token token) {
        // Publish shared XMLTV to every cached channel. Native provider EPG is
        // requested only by the player after a stream link resolves; polling
        // bookmarks or visible catalogue rows creates large repeated HTTP bursts.
        var loader = new EpgApplicationService();
        for(Account account:accounts) {
            refreshVisible(token);
            if(!current(token) || !loader.isEnabled()) return;
            List<String> sources = loader.cacheSources(account);
            if (sources.isEmpty()) continue;
            long now = System.currentTimeMillis();
            boolean sourceDue = sources.stream().anyMatch(source -> due(
                    new EpgCacheDb().sourceState(EpgCacheApplicationService.sourceId(source)), now));
            if (!sourceDue) continue;
            account.setAction(Account.AccountAction.itv);
            Map<String,Channel> channels=new LinkedHashMap<>();
            List<Channel> favourites = bookmarked.getOrDefault(account.getDbId(),List.of());
            favourites.forEach(c -> channels.put(c.getChannelId(),c));
            ChannelDb.get().getCachedLiveChannels(account.getDbId()).forEach(c -> channels.put(c.getChannelId(),c));
            List<Channel> all = new ArrayList<>(channels.values());
            refreshXml(account,all,sources,token);
        }
    }
    private static boolean due(EpgCacheDb.SourceState state,long now) {
        long retry=state.warning().isBlank() ? Math.min(state.retryAt(),state.refreshedAt()+refreshMillis()) : state.retryAt();
        return retry<=now;
    }
    /** Explicit refresh boundary, also usable by import/contract tests without starting timers. */
    public void refreshAccount(Account account,List<Channel> channels) {
        refreshAccount(account,channels,token(),64,List.of());
    }
    /** Native provider EPG is reserved for the currently playing channel. */
    public void refreshPlaybackChannel(Account account, Channel channel) {
        if (channel == null) return;
        refreshAccount(account,List.of(channel),token(),1,List.of(channel));
    }
    private void refreshAccount(Account account,List<Channel> input,Token token,int nativeBudget,List<Channel> nativeChannels) {
        refreshAccount(account,input,token,nativeBudget,nativeChannels,
                account == null ? List.of() : new EpgApplicationService().cacheSources(account));
    }
    private void refreshAccount(Account account,List<Channel> input,Token token,int nativeBudget,List<Channel> nativeChannels,
                                List<String> sources) {
        Map<String,Channel> unique=new LinkedHashMap<>();
        input.forEach(channel -> unique.putIfAbsent(channel.getChannelId(),channel));
        List<Channel> channels=new ArrayList<>(unique.values());
        if(account==null || channels.isEmpty() || !current(token) || !new EpgApplicationService().isEnabled()) return;
        var loader=new EpgApplicationService();
        publishMappings(account,channels,sources,token);
        if(EpgCacheApplicationService.nativeProvider(account) && !nativeChannels.isEmpty()) refreshNative(account,nativeChannels,sources,token,nativeBudget);
        if(current(token)) refreshXml(account,channels,sources,token);
    }
    private void publishMappings(Account account,List<Channel> channels,List<String> sources,Token token) {
        AtomicBoolean mapped = new AtomicBoolean();
        // A routine scheduler pass must not re-run expensive alias/name matching
        // for every cached channel when its persisted identity is unchanged.
        // A source import below deliberately performs a full remap after replacing
        // guide aliases, and changed channel/config identities still remap here.
        commit(token,() -> mapped.set(new EpgCacheDb().mapChannels(
                EpgCacheApplicationService.matches(account,channels,sources),System.currentTimeMillis(),true)));
    }
    private void refreshXml(Account account,List<Channel> channels,List<String> sources,Token token) {
        var loader = new EpgApplicationService();
        var db = new EpgCacheDb();
        // Build the account match index once per refresh cycle.  Previously this was
        // recreated for every XMLTV source, which made a 400k-channel catalogue do
        // the same normalisation and rule work once per source.
        List<EpgCacheDb.Match> mappings = null;
        int importedSources = 0;
        boolean mappingPublished = false;
        boolean needsConsolidatedMapping = false;
        for(String source:sources) {
            if(!current(token) || !loader.isEnabled()) return;
            String id=EpgCacheApplicationService.sourceId(source);
            long now=System.currentTimeMillis();
            if(!due(db.sourceState(id),now)) continue;
            try {
                if (mappings == null) {
                    mappings = EpgCacheApplicationService.matches(account, channels, sources);
                }
                try (XmltvGuide guide=loader.downloadSource(source)) {
                commit(token,() -> db.replaceSource(id,guide.programmes(),guide.names(),guide.qualityNames(),guide.icons(),now,now+refreshMillis()));
                importedSources++;
                if (!mappingPublished && mappings != null && current(token)) {
                    // Publish the first ready account/global source before waiting on
                    // the remaining feeds. This preserves incremental visibility for
                    // the card and guide readers while still avoiding one pass per source.
                    List<EpgCacheDb.Match> preparedMappings = mappings;
                    commit(token, () -> db.mapChannelsWithReport(preparedMappings,
                            System.currentTimeMillis(), false, _ -> { }));
                    mappingPublished = true;
                } else {
                    needsConsolidatedMapping = true;
                }
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.io.IOException | javax.xml.stream.XMLStreamException | RuntimeException error) {
                if(!current(token)) return;
                commit(token,() -> db.failed(id,now+FAILURE_MS,EpgApplicationService.warning(error)));
                AppLog.addWarningLog(EpgCacheRefreshService.class,"EPG source refresh failed; retaining cached programmes.");
            }
        }
        // All imported source tables are now visible.  Resolve links once against
        // the complete source set so source priority and last-good data are kept,
        // without scanning every account channel once per XMLTV file.
        if (importedSources > 0 && mappings != null && needsConsolidatedMapping && current(token)) {
            List<EpgCacheDb.Match> preparedMappings = mappings;
            try {
                commit(token, () -> db.mapChannelsWithReport(preparedMappings, System.currentTimeMillis(), false, _ -> { }));
            } catch (RuntimeException error) {
                if (current(token)) {
                    AppLog.addWarningLog(EpgCacheRefreshService.class,
                            "EPG source imports completed but the consolidated channel mapping pass failed; retaining imported guides.");
                }
            }
        }
    }
    @SuppressWarnings("java:S1994")
    private void refreshNative(Account account,List<Channel> channels,List<String> sources,Token token,int nativeBudget) {
        var loader = new EpgApplicationService();
        var db = new EpgCacheDb();
        int start;
        synchronized(CURSORS) { start=CURSORS.getOrDefault(account.getDbId()+"|"+nativeBudget,0)%channels.size(); }
        int attempted=0;
        int visited=0;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        for(;visited<channels.size() && attempted<nativeBudget && System.nanoTime()<deadline;visited++) {
            if(!current(token) || !loader.isEnabled()) return;
            Channel channel=channels.get((start+visited)%channels.size());
            String id=EpgCacheApplicationService.nativeSource(account,channel);
            var requestLock=NATIVE_REQUEST_LOCKS.computeIfAbsent(id,_ -> new java.util.concurrent.locks.ReentrantLock());
            if(!requestLock.tryLock()) continue;
            try {
                long now=System.currentTimeMillis();
                if(!due(db.sourceState(id),now)) continue;
                attempted++;
                var nativeService=new NativeEpgApplicationService();
                nativeService.expire(account,channel);
                List<EpgProgramme> programmes=nativeService.schedule(account,channel);
                if(!current(token)) return;
                if(EpgApplicationService.usable(programmes)) {
                    long coverage=programmes.stream().mapToLong(EpgProgramme::stop).max().orElse(now);
                    // A short guide can end before the normal failure back-off window. In that case
                    // the upper clamp bound would be below its lower bound and Math.clamp throws.
                    long minimumRetry = now + FAILURE_MS;
                    long coverageRetry = coverage - FAILURE_MS;
                    long retry = coverageRetry < minimumRetry
                            ? minimumRetry
                            : Math.clamp(now + refreshMillis(), minimumRetry, coverageRetry);
                    commit(token,() -> db.replaceSource(id,Map.of(channel.getChannelId(),programmes),Map.of(),Map.of(),Map.of(),now,retry,
                            Set.of(new EpgChangeNotifications.ProviderIdentity(account.getDbId(), channel.getChannelId()))));
                }
                else commit(token,() -> db.failed(id,now+FAILURE_MS,"epgNoGuide"));
                commit(token,() -> db.mapChannels(EpgCacheApplicationService.matches(account,List.of(channel),sources),System.currentTimeMillis()));
            } finally {
                requestLock.unlock();
            }
        }
        synchronized(CURSORS) { CURSORS.put(account.getDbId()+"|"+nativeBudget,(start+visited)%channels.size()); }
    }
}
