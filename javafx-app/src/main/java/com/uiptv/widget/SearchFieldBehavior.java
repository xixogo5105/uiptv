package com.uiptv.widget;

import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;

final class SearchFieldBehavior {
    private static final String MOUSE_CLEAR_INSTALLED_KEY =
            SearchFieldBehavior.class.getName() + ".mouseClearInstalled";
    private static final String CLEAR_ON_NEXT_MOUSE_PRESS_KEY =
            SearchFieldBehavior.class.getName() + ".clearOnNextMousePress";

    private SearchFieldBehavior() {
    }

    static void installMouseClear(TextField field) {
        if (field == null || Boolean.TRUE.equals(field.getProperties().get(MOUSE_CLEAR_INSTALLED_KEY))) {
            return;
        }
        field.getProperties().put(MOUSE_CLEAR_INSTALLED_KEY, Boolean.TRUE);
        field.getProperties().put(CLEAR_ON_NEXT_MOUSE_PRESS_KEY, Boolean.TRUE);
        field.focusedProperty().addListener((_, _, focused) -> {
            if (Boolean.TRUE.equals(focused)) {
                field.getProperties().put(CLEAR_ON_NEXT_MOUSE_PRESS_KEY, Boolean.FALSE);
            } else {
                field.getProperties().put(CLEAR_ON_NEXT_MOUSE_PRESS_KEY, Boolean.TRUE);
            }
        });
        field.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (Boolean.TRUE.equals(field.getProperties().get(CLEAR_ON_NEXT_MOUSE_PRESS_KEY))) {
                field.clear();
                field.getProperties().put(CLEAR_ON_NEXT_MOUSE_PRESS_KEY, Boolean.FALSE);
            }
        });
    }
}
