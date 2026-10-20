package com.uiptv.application;

import com.uiptv.db.EpgCacheDb;
import com.uiptv.db.SQLConnection;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class EpgRefreshLifecycleTest extends DbBackedTest {
    @BeforeEach void disableTimer() {
        EpgCacheRefreshService.stop();
        var config=ConfigurationService.getInstance().read(); config.setEpgEnabled(false);
        ConfigurationService.getInstance().save(config);
    }
    @AfterEach void stop() { EpgCacheRefreshService.stop(); }

    @Test void stoppedLifecycleCannotOutliveItsDatabaseOrLeaveAnUncaughtException() throws Exception {
        var errors=new ConcurrentLinkedQueue<Throwable>();
        var previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error) -> {
            if (thread.getName().equals("epg-cache-lifecycle")) errors.add(error);
            else if (previous != null) previous.uncaughtException(thread,error);
        });
        var field=EpgCacheRefreshService.class.getDeclaredField("RECONCILER"); field.setAccessible(true);
        var active=(AtomicReference<Thread>)field.get(null);
        try {
            for (int iteration=0; iteration<40; iteration++) {
                EpgCacheRefreshService.start();
                Thread task=active.get();
                EpgCacheRefreshService.stop();
                assertNull(active.get(),"stop must drain the queued configuration read before database teardown");
                if (task != null) assertFalse(task.isAlive());
                Files.deleteIfExists(testDbFile.toPath());
                SQLConnection.setDatabasePath(testDbFile.getAbsolutePath());
            }
            assertTrue(errors.isEmpty(),() -> "Uncaught lifecycle failures: "+errors);
        } finally {
            EpgCacheRefreshService.stop();
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }

    @Test void lifecyclePauseListenersAndDueChecksAreIdempotent() throws Exception {
        EpgCacheRefreshService.CataloguePause pause = EpgCacheRefreshService.pauseForCatalogueReload();
        pause.close();
        pause.close();

        Runnable listener = () -> { };
        EpgCacheRefreshService.addListener(listener);
        EpgCacheRefreshService.removeListener(listener);
        EpgCacheRefreshService.removeListener(listener);
        EpgCacheRefreshService.requestRefresh();
        EpgCacheRefreshService.stopForApplicationExit();
        EpgCacheRefreshService.stopServer();

        Method due = EpgCacheRefreshService.class.getDeclaredMethod("due", EpgCacheDb.SourceState.class, long.class);
        due.setAccessible(true);
        long now = System.currentTimeMillis();
        assertTrue((Boolean) due.invoke(null, new EpgCacheDb.SourceState(0, 0, ""), now));
        assertFalse((Boolean) due.invoke(null, new EpgCacheDb.SourceState(now, now + 60_000, "warning"), now));
    }
}
