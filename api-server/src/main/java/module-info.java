module com.uiptv.api.server {
    requires com.uiptv.core;
    requires undertow.core;
    requires org.json;
    requires org.apache.commons.io;
    requires jdk.httpserver;
    requires annotations;
    requires static lombok;
    requires java.sql;

    exports com.uiptv.server;
}
