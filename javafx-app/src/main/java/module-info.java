module com.uiptv.gui {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires javafx.graphics;
    requires javafx.media;
    requires java.sql;
    requires org.json;
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.apache.httpcomponents.core5.httpcore5;
    requires transitive uk.co.caprica.vlcj;
    requires transitive uk.co.caprica.vlcj.javafx;
    requires static lombok;

    requires com.uiptv.server;

    opens com.uiptv.ui to javafx.fxml;
    opens com.uiptv.ui.main to javafx.fxml;
    opens com.uiptv.player to javafx.fxml;
    opens com.uiptv.widget to javafx.fxml;
    
    exports com.uiptv.ui;
    exports com.uiptv.ui.util;
    exports com.uiptv.player;
    exports com.uiptv.player.api;
    exports com.uiptv.widget;
    exports com.uiptv;
}
