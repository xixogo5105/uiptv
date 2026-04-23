package com.uiptv.ui.util;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;

import java.util.Locale;

public final class StyleClassDecorator {
    private static final String DECORATED_NODE_KEY = "uiptv_style_decorated";
    private static final String DECORATED_CHILDREN_KEY = "uiptv_children_decorated";

    private StyleClassDecorator() {
    }

    public static void decorate(Node node) {
        if (node == null) {
            return;
        }
        decorateNode(node);
        if (node instanceof Parent parent) {
            attachChildrenListener(parent);
            for (Node child : parent.getChildrenUnmodifiable()) {
                decorate(child);
            }
        }
    }

    private static void decorateNode(Node node) {
        if (Boolean.TRUE.equals(node.getProperties().get(DECORATED_NODE_KEY))) {
            return;
        }
        node.getProperties().put(DECORATED_NODE_KEY, Boolean.TRUE);
        addStyleClass(node.getStyleClass(), "uiptv-node");
        if (node instanceof Control) {
            addStyleClass(node.getStyleClass(), "uiptv-control");
        }
        addStyleClass(node.getStyleClass(), toStyleClass(node.getClass().getSimpleName()));
        if (node.getId() != null && !node.getId().isBlank()) {
            addStyleClass(node.getStyleClass(), "uiptv-id-" + sanitize(node.getId()));
        }
    }

    private static void attachChildrenListener(Parent parent) {
        if (Boolean.TRUE.equals(parent.getProperties().get(DECORATED_CHILDREN_KEY))) {
            return;
        }
        parent.getProperties().put(DECORATED_CHILDREN_KEY, Boolean.TRUE);
        parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) {
                    continue;
                }
                for (Node node : change.getAddedSubList()) {
                    decorate(node);
                }
            }
        });
    }

    private static String toStyleClass(String value) {
        StringBuilder builder = new StringBuilder("uiptv-");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                builder.append('-');
            }
            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toLowerCase(current));
            }
        }
        return builder.toString();
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    }

    private static void addStyleClass(ObservableList<String> classes, String className) {
        if (className == null || className.isBlank() || classes.contains(className)) {
            return;
        }
        classes.add(className);
    }
}
