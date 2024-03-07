package com.uiptv.widget;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;

public class UIptvCombo extends ComboBox {
    public UIptvCombo(String id, String hint, int width) {
        super();
        this.setId(id);
        this.setPromptText(hint);
        this.setPrefWidth(width);
        this.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty) ;
                if (empty || item == null) {
                    setText(hint);
                } else {
                    setText(item);
                }
            }
        });
    }
}
