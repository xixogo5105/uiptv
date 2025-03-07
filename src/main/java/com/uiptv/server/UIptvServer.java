package com.uiptv.server;

import com.uiptv.server.api.json.*;
import com.uiptv.server.html.*;
import com.uiptv.service.ConfigurationService;
import com.sun.net.httpserver.HttpServer;

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
        server.createContext("/accounts.html", new HttpAccountHtmlServer());
        server.createContext("/acc", new HttpAccountHtmlServer());
        server.createContext("/categories.html", new HttpCategoryHtmlServer());
        server.createContext("/channels.html", new HttpChannelHtmlServer());
        server.createContext("/play.html", new HttpPlayerHtmlServer());
        server.createContext("/", new HttpBookmarksHtmlServer());
        server.createContext("/bookmarks.html", new HttpBookmarksHtmlServer());
        server.createContext("/index.hml", new HttpBookmarksHtmlServer());

        server.createContext("/javascript", new HttpJavascriptServer());
        server.createContext("/js", new HttpJavascriptServer());
        server.createContext("/css", new HttpJavascriptServer());
        server.createContext("/accounts", new HttpAccountJsonServer());
        server.createContext("/categories", new HttpCategoryJsonServer());
        server.createContext("/channels", new HttpChannelJsonServer());
        server.createContext("/player", new HttpPlayerJsonServer());
        server.createContext("/bookmarks", new HttpBookmarksJsonServer());
        server.createContext("/playlist.m3u8", new HttpM3u8PlayListServer());
        server.createContext("/bookmarkEntry.ts", new HttpM3u8BookmarkEntry());
        server.createContext("/bookmarks.m3u8", new HttpM3u8BookmarkPlayListServer());
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