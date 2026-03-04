package com.uiptv.widget;

import javafx.scene.control.ButtonType;

public class DialogAlert {
    public static ButtonType showDialog(String contents) {
        return showDialog("Confirm", contents);
    }

    public static ButtonType showDialog(String title, String contents) {
        return ThemedDialogs.showConfirm(title, contents, ButtonType.YES, ButtonType.NO);
    }
}
