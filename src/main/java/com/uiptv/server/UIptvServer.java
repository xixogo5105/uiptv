package com.uiptv.server;

import com.sun.net.httpserver.HttpServer;
import com.uiptv.server.api.json.*;
import com.uiptv.server.html.HttpSpaHtmlServer;
import com.uiptv.service.ConfigurationService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showMessage;

public class UIptvServer {
    private static final int MIN_HTTP_WORKERS = 20;
    private static HttpServer httpServer;
    private static ExecutorService httpExecutor;

    private static void initialiseServer() throws IOException {
        try {
            stop();
            String httpPort = getHttpPort();
            InetSocketAddress httpAddress = new InetSocketAddress("0.0.0.0", Integer.parseInt(httpPort));

            // Initialize HTTP server
            httpServer = HttpServer.create(httpAddress, 0);
            configureServer(httpServer);

            // Ensure at least 20 concurrent worker threads for web/API traffic.
            int workerThreads = Math.max(MIN_HTTP_WORKERS, Runtime.getRuntime().availableProcessors() * 4);
            httpExecutor = Executors.newFixedThreadPool(workerThreads, namedThreadFactory("uiptv-http-"));
            httpServer.setExecutor(httpExecutor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void configureServer(HttpServer server) {
        // SPA routes
        server.createContext("/", new HttpSpaHtmlServer());
        server.createContext("/index.html", new HttpSpaHtmlServer());
        server.createContext("/myflix.html", new HttpSpaHtmlServer("myflix.html"));
        
        // PWA routes
        server.createContext("/manifest.json", new HttpManifestServer());
        server.createContext("/sw.js", new HttpJavascriptServer());
        
        // Assets
        server.createContext("/icon.ico", new HttpIconServer());

        // Static file servers
        server.createContext("/javascript", new HttpJavascriptServer());
        server.createContext("/js", new HttpJavascriptServer());
        server.createContext("/css", new HttpCssServer());
        
        // HLS Stream Server (Memory-based)
        server.createContext("/hls", new HttpHlsFileServer());
        server.createContext("/hls-upload", new HttpHlsUploadServer());
        
        // API JSON servers
        server.createContext("/accounts", new HttpAccountJsonServer());
        server.createContext("/categories", new HttpCategoryJsonServer());
        server.createContext("/channels", new HttpChannelJsonServer());
        server.createContext("/seriesEpisodes", new HttpSeriesEpisodesJsonServer());
        server.createContext("/seriesDetails", new HttpSeriesDetailsJsonServer());
        server.createContext("/vodDetails", new HttpVodDetailsJsonServer());
        server.createContext("/player", new HttpPlayerJsonServer());
        server.createContext("/bookmarks", new HttpBookmarksJsonServer());
        server.createContext("/playlist.m3u8", new HttpM3u8PlayListServer());
        server.createContext("/bookmarkEntry.ts", new HttpM3u8BookmarkEntry());
        server.createContext("/bookmarks.m3u8", new HttpM3u8BookmarkPlayListServer());
        server.createContext("/iptv.m3u8", new HttpIptvM3u8Server());
        server.createContext("/iptv.m3u", new HttpIptvM3u8Server());
    }

    private static String getHttpPort() {
        return isBlank(ConfigurationService.getInstance().read().getServerPort()) ? "8888" : ConfigurationService.getInstance().read().getServerPort();
    }

    public static void start() throws IOException {
        initialiseServer();
        if (httpServer != null) httpServer.start();
        showMessage("Server Started on port " + getHttpPort());
    }

    public static void stop() throws IOException {
        if (httpServer != null) httpServer.stop(1);
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
        showMessage("Server Stopped");
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
