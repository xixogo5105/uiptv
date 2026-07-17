package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;

public final class UiRenderQuality {
    private UiRenderQuality() {
    }

    public static void optimizeTextNode(Node node) {
        if (node == null) {
            return;
        }
        node.setCache(false);
    }

    public static void optimizeLayout(Region region) {
        if (region == null) {
            return;
        }
        region.setSnapToPixel(true);
        region.setCache(false);
    }

    public static void optimizeTree(Parent parent) {
        if (parent == null) {
            return;
        }
        parent.setCache(false);
        if (parent instanceof Region region) {
            region.setSnapToPixel(true);
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            child.setCache(false);
            if (child instanceof Parent childParent) {
                optimizeTree(childParent);
            }
        }
    }
}
