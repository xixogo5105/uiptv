package com.uiptv.server;

import com.uiptv.server.api.json.*;
import com.uiptv.server.html.HttpSpaHtmlServer;
import com.uiptv.service.ConfigurationService;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;

import static com.uiptv.util.AppLog.addInfoLog;
import static com.uiptv.util.ServerUrlUtil.SERVER_BIND_ADDRESS;
import static com.uiptv.util.StringUtils.isBlank;

public class UIptvServer {
    private static final int MIN_HTTP_WORKERS = 20;
    private static Undertow httpServer;

    private UIptvServer() {
    }

    private static void initialiseServer() {
        stop();
        String httpPort = getHttpPort();
        int port = Integer.parseInt(httpPort);
        int workerThreads = Math.max(MIN_HTTP_WORKERS, Runtime.getRuntime().availableProcessors() * 4);
        int ioThreads = Math.max(2, Runtime.getRuntime().availableProcessors());

        httpServer = Undertow.builder()
                .addHttpListener(port, SERVER_BIND_ADDRESS)
                .setIoThreads(ioThreads)
                .setWorkerThreads(workerThreads)
                .setHandler(configureServer())
                .build();
    }

    private static HttpHandler configureServer() {
        PathHandler routes = new PathHandler();

        // SPA routes
        routes.addExactPath("/", adapt(new HttpSpaHtmlServer()));
        routes.addExactPath("/index.html", adapt(new HttpSpaHtmlServer()));
        routes.addExactPath("/myflix.html", adapt(new HttpSpaHtmlServer("myflix.html")));
        routes.addExactPath("/player.html", adapt(new HttpSpaHtmlServer("player.html")));
        routes.addExactPath("/drm.html", adapt(new HttpSpaHtmlServer("player.html")));

        // PWA routes
        routes.addExactPath("/manifest.json", adapt(new HttpManifestServer()));
        routes.addExactPath("/sw.js", adapt(new HttpJavascriptServer()));

        // Assets
        routes.addExactPath("/icon.ico", adapt(new HttpIconServer()));

        // Static file servers
        routes.addPrefixPath("/javascript", adapt(new HttpJavascriptServer()));
        routes.addPrefixPath("/js", adapt(new HttpJavascriptServer()));
        routes.addPrefixPath("/css", adapt(new HttpCssServer()));

        // HLS Stream Server (Memory-based)
        routes.addPrefixPath("/hls", adapt(new HttpHlsFileServer()));
        routes.addPrefixPath("/hls-upload", adapt(new HttpHlsUploadServer()));
        routes.addPrefixPath("/proxy-stream", adapt(new HttpProxyStreamServer()));
        routes.addExactPath("/bingewatch.m3u8", adapt(new HttpBingeWatchPlaylistServer()));
        routes.addPrefixPath("/bingwatch", adapt(new HttpBingeWatchEntryServer()));

        // API JSON servers
        routes.addExactPath("/accounts", adapt(new HttpAccountJsonServer()));
        routes.addExactPath("/categories", adapt(new HttpCategoryJsonServer()));
        routes.addExactPath("/channels", adapt(new HttpChannelJsonServer()));
        routes.addExactPath("/seriesEpisodes", adapt(new HttpSeriesEpisodesJsonServer()));
        routes.addExactPath("/seriesDetails", adapt(new HttpSeriesDetailsJsonServer()));
        routes.addExactPath("/watchingNow", adapt(new HttpWatchingNowJsonServer()));
        routes.addExactPath("/watchingNowSeriesEpisodes", adapt(new HttpWatchingNowSeriesEpisodesJsonServer()));
        routes.addExactPath("/watchingNowSeriesAction", adapt(new HttpWatchingNowSeriesActionServer()));
        routes.addExactPath("/watchingNowVod", adapt(new HttpWatchingNowVodJsonServer()));
        routes.addExactPath("/watchingNowVodAction", adapt(new HttpWatchingNowVodActionServer()));
        routes.addExactPath("/vodDetails", adapt(new HttpVodDetailsJsonServer()));
        // Single player gateway: /player is canonical, legacy /player/* paths are handled by prefix routing.
        routes.addPrefixPath("/player", adapt(new HttpPlayerGatewayServer()));
        routes.addExactPath("/bookmarks", adapt(new HttpBookmarksJsonServer()));
        routes.addExactPath("/config", adapt(new HttpConfigJsonServer()));
        routes.addExactPath("/remote-sync/health", adapt(new HttpRemoteSyncHealthServer()));
        routes.addExactPath("/remote-sync/request", adapt(new HttpRemoteSyncRequestServer()));
        routes.addExactPath("/remote-sync/status", adapt(new HttpRemoteSyncStatusServer()));
        routes.addExactPath("/remote-sync/upload", adapt(new HttpRemoteSyncUploadServer()));
        routes.addExactPath("/remote-sync/download", adapt(new HttpRemoteSyncDownloadServer()));
        routes.addExactPath("/remote-sync/complete", adapt(new HttpRemoteSyncCompleteServer()));
        routes.addExactPath("/playlist.m3u8", adapt(new HttpM3u8PlayListServer()));
        routes.addExactPath("/bookmarkEntry.ts", adapt(new HttpM3u8BookmarkEntry()));
        routes.addExactPath("/bookmarks.m3u8", adapt(new HttpM3u8BookmarkPlayListServer()));
        routes.addExactPath("/iptv.m3u8", adapt(new HttpIptvM3u8Server()));
        routes.addExactPath("/iptv.m3u", adapt(new HttpIptvM3u8Server()));

        routes.addPrefixPath("/", adapt(new HttpSpaHtmlServer()));
        return exchange -> {
            exchange.putAttachment(UndertowAttachments.attributes(), UndertowAttachments.newAttributes());
            routes.handleRequest(exchange);
        };
    }

    private static HttpHandler adapt(com.sun.net.httpserver.HttpHandler handler) {
        return new UndertowHttpHandlerAdapter(handler);
    }

    private static String getHttpPort() {
        return isBlank(ConfigurationService.getInstance().read().getServerPort()) ? "8888" : ConfigurationService.getInstance().read().getServerPort();
    }

    public static synchronized void start() {
        initialiseServer();
        httpServer.start();
        addInfoLog(UIptvServer.class, "Server Started on port " + getHttpPort());
    }

    public static synchronized boolean ensureStarted() {
        if (isRunning()) {
            return false;
        }
        initialiseServer();
        httpServer.start();
        addInfoLog(UIptvServer.class, "Server Started on port " + getHttpPort());
        return true;
    }

    public static synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        addInfoLog(UIptvServer.class, "Server Stopped");
    }

    public static synchronized boolean isRunning() {
        return httpServer != null;
    }
}
