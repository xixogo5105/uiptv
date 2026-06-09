package com.uiptv.widget;

import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;

final class SearchFieldBehavior {
    private static final String MOUSE_CLEAR_INSTALLED_KEY =
            SearchFieldBehavior.class.getName() + ".mouseClearInstalled";

    private SearchFieldBehavior() {
    }

    static void installMouseClear(TextField field) {
        if (field == null || Boolean.TRUE.equals(field.getProperties().get(MOUSE_CLEAR_INSTALLED_KEY))) {
            return;
        }
        field.getProperties().put(MOUSE_CLEAR_INSTALLED_KEY, Boolean.TRUE);
        field.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> field.clear());
    }
}
