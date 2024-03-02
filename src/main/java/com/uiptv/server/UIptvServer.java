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
    private static HttpServer server;

    private static void initialiseServer() throws IOException {
        try {
            stop();
            String serverPort = ConfigurationService.getInstance().read().getServerPort();
            server = HttpServer.create(new InetSocketAddress(isBlank(serverPort) ? 8888 : Integer.valueOf(serverPort)), 0);
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

            server.setExecutor(null); // creates a default executor
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void start() throws IOException {
        initialiseServer();
        if (server != null) server.start();
        showMessage("Server Started on port " + ConfigurationService.getInstance().read().getServerPort());
    }

    public static void stop() throws IOException {
        if (server != null) server.stop(1);
        showMessage("Server Stopped");
    }
}
