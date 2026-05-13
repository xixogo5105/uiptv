package com.uiptv.widget;
import com.uiptv.ui.util.*;

import com.uiptv.util.I18n;
import javafx.scene.control.TextField;

public class UIptvText extends TextField {
    public UIptvText(String id, String promptKey, int columnCount) {
        super();
        this.setId(id);
        this.setPromptText(I18n.tr(promptKey));
        this.setPrefColumnCount(columnCount);
    }
}
