package com.uiptv.widget;

import javafx.scene.control.TextField;

public class UIptvText extends TextField {
    public UIptvText(String id, String hint, int columnCount) {
        super();
        this.setId(id);
        this.setPromptText(hint);
        this.setPrefColumnCount(columnCount);
    }
}
