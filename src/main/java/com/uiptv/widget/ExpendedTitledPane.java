package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;

public class ExpendedTitledPane extends TitledPane {

    public ExpendedTitledPane(String title, Node content) {
        super(title, content);
        setCollapsible(true);
        setExpanded(true);

    }
}
