package com.uiptv.widget;
import com.uiptv.ui.util.*;

import javafx.scene.control.Button;

public class ProminentButton extends Button {

    public ProminentButton(String text) {
        super(text);
        getStyleClass().add("prominent");
    }

}
