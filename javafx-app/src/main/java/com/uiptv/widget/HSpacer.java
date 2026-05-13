package com.uiptv.widget;

import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class HSpacer extends Region {
    public HSpacer(double width) {
        super();
        setPrefWidth(width);
        VBox.setVgrow(this, Priority.ALWAYS);
    }
}
