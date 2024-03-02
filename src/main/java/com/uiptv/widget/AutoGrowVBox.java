package com.uiptv.widget;

import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AutoGrowVBox extends VBox {

    public AutoGrowVBox(int i, TextField text, SearchableTableView table) {
        super(i, text, table);
        autoGrow();
    }

    protected void autoGrow() {
        this.getChildren().forEach(child -> VBox.setVgrow(child, Priority.ALWAYS));
    }
}
