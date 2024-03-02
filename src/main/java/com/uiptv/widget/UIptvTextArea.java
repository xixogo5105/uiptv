package com.uiptv.widget;

import javafx.scene.control.TextArea;

public class UIptvTextArea extends TextArea {
    public UIptvTextArea(String id, String hint, int columnCount) {
        super();
        this.setId(id);
        this.setWrapText(true);
        this.setPromptText(hint);
        this.setHeight(200);
        this.setPrefColumnCount(columnCount);
    }
}
