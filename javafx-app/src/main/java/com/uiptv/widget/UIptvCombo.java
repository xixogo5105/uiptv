package com.uiptv.widget;
import com.uiptv.ui.util.*;

import com.uiptv.util.I18n;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

public class UIptvCombo extends ComboBox<String> {
    public UIptvCombo(String id, String promptKey, int width) {
        super();
        this.setId(id);
        this.setPromptText(I18n.tr(promptKey));
        this.setPrefWidth(width);
        this.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty) ;
                if (empty || item == null) {
                    setText(I18n.tr(promptKey));
                } else {
                    setText(item);
                }
            }
        });
    }
}
