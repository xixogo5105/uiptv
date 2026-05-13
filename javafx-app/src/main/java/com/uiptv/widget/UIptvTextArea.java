package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.scene.control.TextArea;

public class UIptvTextArea extends TextArea {
    public UIptvTextArea(String id, String promptKey, int columnCount) {
        super();
        this.setId(id);
        this.setWrapText(true);
        this.setPromptText(I18n.tr(promptKey));
        this.setHeight(200);
        this.setPrefColumnCount(columnCount);
    }
}
