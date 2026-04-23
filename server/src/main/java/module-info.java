module com.uiptv.server {
    requires org.json;
    requires org.apache.commons.io;
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.apache.httpcomponents.core5.httpcore5;
    requires jdk.net;
    requires jdk.httpserver;
    requires net.bjoernpetersen.m3u;
    requires java.sql;
    requires com.rometools.rome;
    requires org.slf4j;
    requires io.github.willena.sqlitejdbc;
    requires annotations;
    requires static lombok;

    exports com.uiptv.api;
    exports com.uiptv.util;
    exports com.uiptv.server;
    exports com.uiptv.server.api.json;
    exports com.uiptv.server.html;
    exports com.uiptv.service;
    exports com.uiptv.service.cache;
    exports com.uiptv.model;
    exports com.uiptv.shared;
    exports com.uiptv.db;

    opens com.uiptv.model to org.json;
}
