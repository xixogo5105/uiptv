package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;

public class ExpandedTitledPane extends TitledPane {

    public ExpandedTitledPane(String title, Node content) {
        super(title, content);
        setCollapsible(true);
        setExpanded(true);

    }
}
