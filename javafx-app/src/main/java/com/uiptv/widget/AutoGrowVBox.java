package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AutoGrowVBox extends VBox {

    public AutoGrowVBox(int i, Node searchNode, TableView<?> table) {
        super(i, searchNode, table);
        autoGrow();
    }

    protected void autoGrow() {
        this.getChildren().forEach(child -> VBox.setVgrow(child, Priority.ALWAYS));
    }
}
