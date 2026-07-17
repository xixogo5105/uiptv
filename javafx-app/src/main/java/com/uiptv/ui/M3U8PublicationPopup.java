package com.uiptv.ui;

import javafx.stage.Stage;

public class M3U8PublicationPopup extends M3U8PublicationInline {
    public M3U8PublicationPopup(Stage stage) {
        super(stage == null ? null : stage::close);
    }
}
