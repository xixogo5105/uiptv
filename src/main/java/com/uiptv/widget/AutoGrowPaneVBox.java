package com.uiptv.widget;

import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AutoGrowPaneVBox extends VBox {

    public AutoGrowPaneVBox(int i, Pane pane, TableView table) {
        super(i, pane, table);
        autoGrow();
    }
    protected void autoGrow() {
        this.getChildren().forEach(child -> VBox.setVgrow(child, Priority.ALWAYS));
    }
}
