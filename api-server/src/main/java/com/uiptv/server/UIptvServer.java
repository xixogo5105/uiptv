package com.uiptv.server;

import com.uiptv.server.api.json.*;
import com.uiptv.server.html.HttpSpaHtmlServer;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.sun.net.httpserver.HttpHandler;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.List;

import static com.uiptv.util.AppLog.addInfoLog;
import static com.uiptv.util.ServerUrlUtil.*;

public class UIptvServer {
    private static final int MIN_HTTP_WORKERS = 20;
    private static Server httpServer;

    private UIptvServer() {
    }

    private static void initialiseServer() throws IOException {
        stop();
        String httpPort = getHttpPort();
        int port = Integer.parseInt(httpPort);
        boolean httpsEnabled = isHttpsServerEnabled();
        String httpsPort = getHttpsPort();
        int securePort = Integer.parseInt(httpsPort);
        if (httpsEnabled && securePort == port) {
            throw new IOException("HTTPS server port must be different from HTTP server port.");
        }
        int workerThreads = Math.max(MIN_HTTP_WORKERS, Runtime.getRuntime().availableProcessors() * 4);
        int ioThreads = Math.max(2, Runtime.getRuntime().availableProcessors());

        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMinThreads(ioThreads);
        threadPool.setMaxThreads(workerThreads);
        httpServer = new Server(threadPool);

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        httpConfig.setRequestHeaderSize(8192);
        httpConfig.setResponseHeaderSize(8192);

        List<String> bindAddresses = getServerBindAddresses();
        for (String bindAddress : bindAddresses) {
            ServerConnector httpConnector = new ServerConnector(httpServer,
                    new HttpConnectionFactory(httpConfig));
            httpConnector.setHost(bindAddress);
            httpConnector.setPort(port);
            httpServer.addConnector(httpConnector);
        }

        if (httpsEnabled) {
            var sslContext = LocalHttpsCertificateStore.sslContext(bindAddresses);
            HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());
            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setSslContext(sslContext);
            
            for (String bindAddress : bindAddresses) {
                ServerConnector httpsConnector = new ServerConnector(httpServer,
                        new SslConnectionFactory(sslContextFactory, "http/1.1"),
                        new HttpConnectionFactory(httpsConfig));
                httpsConnector.setHost(bindAddress);
                httpsConnector.setPort(securePort);
                httpServer.addConnector(httpsConnector);
            }
        }

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        configureServer(context);
        httpServer.setHandler(context);
    }

    private static void configureServer(ServletContextHandler context) {
        Filter loggingFilter = new WebRequestActivityLoggingFilter();
        context.addFilter(new FilterHolder(loggingFilter), "/*", null);

        // SPA routes
        addServlet(context, "/", new HttpSpaHtmlServer());
        addServlet(context, "/index.html", new HttpSpaHtmlServer());
        addServlet(context, "/drm.html", new HttpSpaHtmlServer());

        // PWA routes
        addServlet(context, "/manifest.json", new HttpManifestServer());
        addServlet(context, "/sw.js", new HttpJavascriptServer());

        // Assets
        addServlet(context, "/icon.ico", new HttpIconServer());
        addServlet(context, "/icon.png", new HttpIconServer());
        addServlet(context, "/icon-192.png", new HttpIconServer());
        addServlet(context, "/icon-512.png", new HttpIconServer());
        addServlet(context, "/icon-maskable-512.png", new HttpIconServer());

        // Static file servers
        addServlet(context, "/javascript/*", new HttpJavascriptServer());
        addServlet(context, "/js/*", new HttpJavascriptServer());
        addServlet(context, "/css/*", new HttpCssServer());
        addServlet(context, "/images/*", new HttpImageServer());

        // Local stream proxy for web playback.
        addServlet(context, "/proxy-stream/*", new HttpProxyStreamServer());
        addServlet(context, "/bingewatch.m3u8", new HttpBingeWatchPlaylistServer());
        addServlet(context, "/bingwatch/*", new HttpBingeWatchEntryServer());

        // API JSON servers
        addServlet(context, "/accounts", new HttpAccountJsonServer());
        addServlet(context, "/categories", new HttpCategoryJsonServer());
        addServlet(context, "/channels", new HttpChannelJsonServer());
        addServlet(context, "/seriesEpisodes", new HttpSeriesEpisodesJsonServer());
        addServlet(context, "/seriesDetails", new HttpSeriesDetailsJsonServer());
        addServlet(context, "/bingeWatchSession", new HttpBingeWatchSessionJsonServer());
        addServlet(context, "/watchingNow", new HttpWatchingNowJsonServer());
        addServlet(context, "/watchingNowSeriesEpisodes", new HttpWatchingNowSeriesEpisodesJsonServer());
        addServlet(context, "/watchingNowSeriesAction", new HttpWatchingNowSeriesActionServer());
        addServlet(context, "/watchingNowVod", new HttpWatchingNowVodJsonServer());
        addServlet(context, "/watchingNowVodAction", new HttpWatchingNowVodActionServer());
        addServlet(context, "/vodDetails", new HttpVodDetailsJsonServer());
        // Single player gateway: /player is canonical, legacy /player/* paths are handled by prefix routing.
        addServlet(context, "/player/*", new HttpPlayerGatewayServer());
        addServlet(context, "/bookmarks", new HttpBookmarksJsonServer());
        addServlet(context, "/config", new HttpConfigJsonServer());
        addServlet(context, "/remote-sync/health", new HttpRemoteSyncHealthServer());
        addServlet(context, "/remote-sync/request", new HttpRemoteSyncRequestServer());
        addServlet(context, "/remote-sync/status", new HttpRemoteSyncStatusServer());
        addServlet(context, "/remote-sync/upload", new HttpRemoteSyncUploadServer());
        addServlet(context, "/remote-sync/download", new HttpRemoteSyncDownloadServer());
        addServlet(context, "/remote-sync/complete", new HttpRemoteSyncCompleteServer());
        addServlet(context, "/playlist.m3u8", new HttpM3u8PlayListServer());
        addServlet(context, "/bookmarkEntry.ts", new HttpM3u8BookmarkEntry());
        addServlet(context, "/bookmarks.m3u8", new HttpM3u8BookmarkPlayListServer());
        addServlet(context, "/iptv.m3u8", new HttpIptvM3u8Server());
        addServlet(context, "/iptv.m3u", new HttpIptvM3u8Server());

        // Fallback for SPA
        addServlet(context, "/*", new HttpSpaHtmlServer());
    }

    private static void addServlet(ServletContextHandler context, String pathSpec, HttpHandler handler) {
        context.addServlet(new ServletHolder(new JettyHttpHandlerAdapter(handler)), pathSpec);
    }

    private static String getHttpPort() {
        return getConfiguredServerPort();
    }

    private static String getHttpsPort() {
        return getConfiguredHttpsServerPort();
    }

    public static synchronized void start() throws IOException {
        initialiseServer();
        try {
            httpServer.start();
        } catch (Exception e) {
            throw new IOException("Failed to start server", e);
        }
        addServerStartedLog();
    }

    public static synchronized boolean ensureStarted() throws IOException {
        if (isRunning()) {
            return false;
        }
        initialiseServer();
        try {
            httpServer.start();
        } catch (Exception e) {
            throw new IOException("Failed to start server", e);
        }
        addServerStartedLog();
        return true;
    }

    public static synchronized void stop() {
        if (httpServer != null) {
            try {
                httpServer.stop();
            } catch (Exception e) {
                addInfoLog(UIptvServer.class, "Error stopping server: " + e.getMessage());
            }
            httpServer = null;
            addInfoLog(UIptvServer.class, "Server Stopped");
        }
    }

    public static synchronized boolean isRunning() {
        return httpServer != null && httpServer.isRunning();
    }

    private static void addServerStartedLog() {
        String message = "Server Started on HTTP port " + getHttpPort();
        if (isHttpsServerEnabled()) {
            message += " and HTTPS port " + getHttpsPort();
        }
        addInfoLog(UIptvServer.class, message);
    }
}