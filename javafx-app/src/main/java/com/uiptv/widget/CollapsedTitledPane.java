package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;

public class CollapsedTitledPane extends TitledPane {

    public CollapsedTitledPane(String title, Node content) {
        super(title, content);
        setCollapsible(true);
        setExpanded(false);

    }
}
