module com.uiptv.api.server {
    requires com.uiptv.core;
    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.ee9.servlet;
    requires org.eclipse.jetty.http;
    requires org.eclipse.jetty.io;
    requires org.eclipse.jetty.util;
    requires org.eclipse.jetty.ee9.websocket.jetty.api;
    requires org.eclipse.jetty.ee9.websocket.jetty.server;
    requires org.json;
    requires org.apache.commons.io;
    requires jdk.httpserver;
    requires annotations;
    requires static lombok;
    requires java.sql;

    exports com.uiptv.server;
}
