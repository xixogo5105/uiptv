module com.uiptv {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
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
    requires javafx.graphics;
    requires annotations;
    requires javafx.media;

    requires transitive uk.co.caprica.vlcj;
    requires transitive uk.co.caprica.vlcj.javafx;
    requires static lombok;

    opens com.uiptv.ui to javafx.fxml;
    exports com.uiptv.ui;
    exports com.uiptv.api;
    exports com.uiptv.util;
    exports com.uiptv.widget;
    exports com.uiptv.service;
    exports com.uiptv.model;
    opens com.uiptv.util to javafx.fxml;
    exports com.uiptv.shared;
    exports com.uiptv.player;
    opens com.uiptv.player to javafx.fxml;
}
