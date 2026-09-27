module com.uiptv.api.server {
    requires com.uiptv.core;
    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.servlet;
    requires org.eclipse.jetty.http;
    requires org.eclipse.jetty.io;
    requires org.eclipse.jetty.util;
    requires org.json;
    requires org.apache.commons.io;
    requires jdk.httpserver;
    requires annotations;
    requires static lombok;
    requires java.sql;

    exports com.uiptv.server;
}
