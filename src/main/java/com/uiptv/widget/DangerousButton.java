package com.uiptv.widget;

import javafx.scene.control.Button;

public class DangerousButton extends Button {
    public DangerousButton(String text) {
        super(text);
        getStyleClass().add("dangerous");
    }
}
