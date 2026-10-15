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
    private static long refreshMillis() {
        return TimeUnit.HOURS.toMillis(ConfigurationService.getInstance().read().normalizedEpgRefreshHours());
    }
    private static final long FAILURE_MS=TimeUnit.MINUTES.toMillis(5);
    private static final Object COMMIT_LOCK=new Object();
    private static final AtomicLong EPOCH=new AtomicLong(), REVISION=new AtomicLong();
    private static final CopyOnWriteArraySet<Runnable> LISTENERS=new CopyOnWriteArraySet<>();
    private static ScheduledExecutorService scheduler;
    private static boolean applicationOwned;
    public static synchronized void startApplication() { applicationOwned=true; start(); }
    public static void stopServer() { stop(true); }
    private static final ConfigurationChangeListener CONFIG_CHANGED=_ -> settingsChanged();
    private static final AccountChangeListener ACCOUNTS_CHANGED=_ -> settingsChanged();
    private static void settingsChanged() {
        EPOCH.incrementAndGet();
        WORKERS.forEach(Thread::interrupt);
        stopTimer();
        reconcileSoon();
    }
    private static final Set<Thread> WORKERS=ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean QUEUED=new AtomicBoolean();
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
    private static final Map<String,Integer> CURSORS=new HashMap<>();
    private record Token(String path,long epoch) { }
    private static Token token() { return new Token(SQLConnection.getDatabasePath(),EPOCH.get()); }
    private static boolean current(Token token) { return CATALOGUE_RELOADS.get() == 0 && token.epoch()==EPOCH.get() && token.path().equals(SQLConnection.getDatabasePath()) && !Thread.currentThread().isInterrupted(); }
    private static void commit(Token token,Runnable write) {
        synchronized(COMMIT_LOCK) { if(current(token)) write.run(); }
    }
    private static void changed() { REVISION.incrementAndGet(); LISTENERS.forEach(Runnable::run); }
    public static long revision() { return REVISION.get(); }
    public static void addListener(Runnable listener) { LISTENERS.add(listener); }
    public static void removeListener(Runnable listener) { LISTENERS.remove(listener); }

    private static volatile boolean lifecycleStarted;
    private static final AtomicReference<Thread> RECONCILER=new AtomicReference<>();
    public static synchronized void start() {
        if(!lifecycleStarted) {
            lifecycleStarted=true;
            ConfigurationService.getInstance().addChangeListener(CONFIG_CHANGED);
            AccountService.getInstance().addChangeListener(ACCOUNTS_CHANGED);
        }
        reconcileSoon();
    }
    private static synchronized void reconcileSoon() {
        if (!lifecycleStarted || RECONCILER.get() != null) return;
        long epoch = EPOCH.get();
        Thread task = Thread.ofVirtual().name("epg-cache-lifecycle").unstarted(() -> {
            try {
                if (!lifecycleStarted || epoch != EPOCH.get() || Thread.currentThread().isInterrupted()) return;
                boolean enabled = new EpgApplicationService().isEnabled();
                synchronized (EpgCacheRefreshService.class) {
                    if (!lifecycleStarted || epoch != EPOCH.get() || Thread.currentThread().isInterrupted()) return;
                    if (!enabled) stopTimer();
                    else if (scheduler == null || scheduler.isShutdown()) {
                        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread thread = new Thread(r,"epg-cache-refresh"); thread.setDaemon(true); return thread; });
                        scheduler.scheduleWithFixedDelay(EpgCacheRefreshService::runScheduled,0,60,TimeUnit.SECONDS);
                    }
                }
            } catch (DatabaseAccessException error) {
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
    private static synchronized void stopTimer() {
        if(scheduler!=null) scheduler.shutdownNow(); scheduler=null; QUEUED.set(false);
    }
    public static void stop() { stop(false); }
    private static void stop(boolean serverOnly) {
        Thread lifecycleTask;
        ScheduledExecutorService timer;
        List<Thread> workers;
        synchronized (EpgCacheRefreshService.class) {
            if (serverOnly && applicationOwned) return;
            lifecycleStarted = false;
            EPOCH.incrementAndGet();
            ConfigurationService.getInstance().removeChangeListener(CONFIG_CHANGED);
            AccountService.getInstance().removeChangeListener(ACCOUNTS_CHANGED);
            lifecycleTask = RECONCILER.get();
            if (lifecycleTask != null) lifecycleTask.interrupt();
            workers = new ArrayList<>(WORKERS);
            workers.forEach(Thread::interrupt);
            timer = scheduler;
            stopTimer(); applicationOwned = false;
        }
        // Wait outside the lifecycle lock: finishing tasks need it to release ownership.
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            if (lifecycleTask != null) workers.add(lifecycleTask);
            for (Thread task : workers) {
                long remaining = deadline - System.nanoTime();
                if (task != Thread.currentThread() && remaining > 0)
                    task.join(java.time.Duration.ofNanos(remaining));
            }
            long remaining = deadline - System.nanoTime();
            if (timer != null && !workers.contains(Thread.currentThread()) && remaining > 0)
                timer.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    /** Wake a running scheduler, but never create an unmanaged worker from a read path. */
    public static synchronized void requestRefresh() {
        if(scheduler==null || scheduler.isShutdown() || !QUEUED.compareAndSet(false,true)) return;
        scheduler.execute(() -> { QUEUED.set(false); runScheduled(); });
    }
    static void invalidate() {
        synchronized(COMMIT_LOCK) {
            EPOCH.incrementAndGet();
            WORKERS.forEach(thread -> { if(thread!=Thread.currentThread()) thread.interrupt(); });
            new EpgCacheDb().clear();
        }
        changed();
    }
    public void clearAndRefresh() {
        new EpgApplicationService().clearCache();
        requestRefresh();
    }
    private static void runScheduled() {
        synchronized (EpgCacheRefreshService.class) {
            if (!lifecycleStarted || CATALOGUE_RELOADS.get() > 0 || Thread.currentThread().isInterrupted()) return;
            WORKERS.add(Thread.currentThread());
        }
        try {
            if(!new EpgApplicationService().isEnabled()) return;
            new EpgCacheRefreshService().refreshDue();
        } catch(Exception error) {
            if(!Thread.currentThread().isInterrupted()) AppLog.addWarningLog(EpgCacheRefreshService.class,"EPG background refresh failed; existing guide remains available.");
        } finally { WORKERS.remove(Thread.currentThread()); Thread.interrupted(); }
    }

    public void refreshDue() {
        if(!new EpgApplicationService().isEnabled()) return;
        Token token=token();
        Map<String,List<Channel>> bookmarked=new HashMap<>();
        for(var resolved:new BookmarkResolver().resolveBookmarks(BookmarkService.getInstance().read())) {
            if(resolved.getAccount()==null || resolved.getAccountAction()!=Account.AccountAction.itv) continue;
            Bookmark bookmark=resolved.getBookmark();
            Channel channel=Channel.fromJson(bookmark.getChannelJson());
            if(channel==null) channel=new Channel();
            channel.setChannelId(bookmark.getChannelId()); channel.setName(bookmark.getChannelName());
            bookmarked.computeIfAbsent(resolved.getAccount().getDbId(),_ -> new ArrayList<>()).add(channel);
        }
        List<Account> accounts = new ArrayList<>(AccountService.getInstance().getAllById().values());
        accounts.sort(Comparator.comparing(account -> bookmarked.getOrDefault(account.getDbId(),List.of()).isEmpty()));
        // Publish shared XMLTV to every cached channel while native bookmark requests get priority.
        // Bulk native rotation follows only after each account has had its initial guide pass.
        for(boolean bulk : new boolean[]{false,true}) for(Account account:accounts) {
            if(!current(token) || !new EpgApplicationService().isEnabled()) return;
            account.setAction(Account.AccountAction.itv);
            Map<String,Channel> channels=new LinkedHashMap<>();
            List<Channel> favourites = bookmarked.getOrDefault(account.getDbId(),List.of());
            favourites.forEach(c -> channels.put(c.getChannelId(),c));
            ChannelDb.get().getCachedLiveChannels(account.getDbId()).forEach(c -> channels.put(c.getChannelId(),c));
            List<Channel> all = new ArrayList<>(channels.values());
            List<Channel> nativeChannels = bulk ? all : favourites.stream().map(c -> channels.get(c.getChannelId())).toList();
            refreshAccount(account,all,token,bulk ? 64 : 16,nativeChannels);
        }
        commit(token,() -> new EpgCacheDb().prune(System.currentTimeMillis()));
    }
    private static boolean due(EpgCacheDb.SourceState state,long now) {
        long retry=state.warning().isBlank() ? Math.min(state.retryAt(),state.refreshedAt()+refreshMillis()) : state.retryAt();
        return retry<=now;
    }
    /** Explicit refresh boundary, also usable by import/contract tests without starting timers. */
    public void refreshAccount(Account account,List<Channel> channels) {
        refreshAccount(account,channels,token(),64);
    }
    private void refreshAccount(Account account,List<Channel> input,Token token,int nativeBudget) {
        refreshAccount(account,input,token,nativeBudget,input);
    }
    private void refreshAccount(Account account,List<Channel> input,Token token,int nativeBudget,List<Channel> nativeChannels) {
        Map<String,Channel> unique=new LinkedHashMap<>();
        input.forEach(channel -> unique.putIfAbsent(channel.getChannelId(),channel));
        List<Channel> channels=new ArrayList<>(unique.values());
        if(account==null || channels.isEmpty() || !current(token) || !new EpgApplicationService().isEnabled()) return;
        var loader=new EpgApplicationService();
        List<String> sources=loader.cacheSources(account);
        publishMappings(account,channels,sources,token);
        if(EpgCacheApplicationService.nativeProvider(account) && !nativeChannels.isEmpty()) refreshNative(account,nativeChannels,sources,token,nativeBudget);
        if(current(token)) refreshXml(account,channels,sources,token);
    }
    private void publishMappings(Account account,List<Channel> channels,List<String> sources,Token token) {
        AtomicBoolean mapped = new AtomicBoolean();
        commit(token,() -> mapped.set(new EpgCacheDb().mapChannels(EpgCacheApplicationService.matches(account,channels,sources),System.currentTimeMillis())));
        if(current(token) && mapped.get()) changed();
    }
    private void refreshXml(Account account,List<Channel> channels,List<String> sources,Token token) {
        var loader = new EpgApplicationService();
        var db = new EpgCacheDb();
        for(String source:sources) {
            if(!current(token) || !loader.isEnabled()) return;
            String id=EpgCacheApplicationService.sourceId(source); long now=System.currentTimeMillis();
            if(!due(db.sourceState(id),now)) continue;
            try {
                XmltvGuide guide=loader.downloadSource(source);
                commit(token,() -> { db.replaceSource(id,guide.programmes(),guide.names(),guide.qualityNames(),guide.icons(),now,now+refreshMillis()); db.mapChannels(EpgCacheApplicationService.matches(account,channels,sources),System.currentTimeMillis()); });
            } catch(Exception error) {
                if(!current(token)) return;
                commit(token,() -> db.failed(id,now+FAILURE_MS,EpgApplicationService.warning(error)));
                AppLog.addWarningLog(EpgCacheRefreshService.class,"EPG source refresh failed; retaining cached programmes.");
            }
            if(current(token)) changed();
        }
    }
    private void refreshNative(Account account,List<Channel> channels,List<String> sources,Token token,int nativeBudget) {
        var loader = new EpgApplicationService();
        var db = new EpgCacheDb();
        int start;
        synchronized(CURSORS) { start=CURSORS.getOrDefault(account.getDbId()+"|"+nativeBudget,0)%channels.size(); }
        int attempted=0,visited=0;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        for(;visited<channels.size() && attempted<nativeBudget && System.nanoTime()<deadline;visited++) {
            if(!current(token) || !loader.isEnabled()) return;
            Channel channel=channels.get((start+visited)%channels.size());
            String id=EpgCacheApplicationService.nativeSource(account,channel); long now=System.currentTimeMillis();
            if(!due(db.sourceState(id),now)) continue;
            attempted++;
            var nativeService=new NativeEpgApplicationService();
            nativeService.expire(account,channel);
            List<EpgProgramme> programmes=nativeService.schedule(account,channel);
            if(!current(token)) return;
            if(EpgApplicationService.usable(programmes)) {
                long coverage=programmes.stream().mapToLong(EpgProgramme::stop).max().orElse(now);
                long retry=Math.min(now+refreshMillis(),Math.max(now+FAILURE_MS,coverage-FAILURE_MS));
                commit(token,() -> db.replaceSource(id,Map.of(channel.getChannelId(),programmes),Map.of(),Map.of(),now,retry));
            }
            else commit(token,() -> db.failed(id,now+FAILURE_MS,"epgNoGuide"));
            commit(token,() -> db.mapChannels(EpgCacheApplicationService.matches(account,List.of(channel),sources),System.currentTimeMillis()));
            if(current(token)) changed();
        }
        synchronized(CURSORS) { CURSORS.put(account.getDbId()+"|"+nativeBudget,(start+visited)%channels.size()); }
    }
}
