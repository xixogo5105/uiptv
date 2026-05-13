package com.uiptv.widget;

import javafx.scene.control.Button;

public class ProminentButton extends Button {

    public ProminentButton(String text) {
        super(text);
        getStyleClass().add("prominent");
    }

}
