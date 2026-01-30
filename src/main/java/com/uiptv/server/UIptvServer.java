package com.uiptv.server;

import com.sun.net.httpserver.HttpServer;
import com.uiptv.server.api.json.*;
import com.uiptv.server.html.HttpSpaHtmlServer;
import com.uiptv.service.ConfigurationService;

import java.io.IOException;
import java.net.InetSocketAddress;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showMessage;

public class UIptvServer {
    private static HttpServer httpServer;

    private static void initialiseServer() throws IOException {
        try {
            stop();
            String httpPort = getHttpPort();
            InetSocketAddress httpAddress = new InetSocketAddress("0.0.0.0", Integer.parseInt(httpPort));

            // Initialize HTTP server
            httpServer = HttpServer.create(httpAddress, 0);
            configureServer(httpServer);

            httpServer.setExecutor(null); // creates a default executor
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void configureServer(HttpServer server) {
        // SPA routes
        server.createContext("/", new HttpSpaHtmlServer());
        server.createContext("/index.html", new HttpSpaHtmlServer());
        
        // PWA routes
        server.createContext("/manifest.json", new HttpManifestServer());
        server.createContext("/sw.js", new HttpJavascriptServer());
        
        // Assets
        server.createContext("/icon.ico", new HttpIconServer());

        // Static file servers
        server.createContext("/javascript", new HttpJavascriptServer());
        server.createContext("/js", new HttpJavascriptServer());
        server.createContext("/css", new HttpCssServer());
        
        // HLS Stream Server
        server.createContext("/hls", new HttpHlsFileServer());
        
        // API JSON servers
        server.createContext("/accounts", new HttpAccountJsonServer());
        server.createContext("/categories", new HttpCategoryJsonServer());
        server.createContext("/channels", new HttpChannelJsonServer());
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
        showMessage("Server Stopped");
    }
}
