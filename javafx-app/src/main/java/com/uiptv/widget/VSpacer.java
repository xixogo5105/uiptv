package com.uiptv.widget;
import com.uiptv.ui.util.*;

import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class VSpacer extends Region {
    public VSpacer(double height) {
        super();
        setPrefHeight(height);
        VBox.setVgrow(this, Priority.ALWAYS);
    }
}
