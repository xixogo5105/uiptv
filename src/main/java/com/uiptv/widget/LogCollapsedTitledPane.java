package com.uiptv.widget;

import com.uiptv.ui.LogDisplayUI;

public class LogCollapsedTitledPane extends CollapsedTitledPane {

    public LogCollapsedTitledPane(String title, LogDisplayUI content) {
        super(title, content);

        expandedProperty().addListener((observable, oldValue, newValue) -> {
            LogDisplayUI.setLoggingEnabled(newValue);
        });
    }
}