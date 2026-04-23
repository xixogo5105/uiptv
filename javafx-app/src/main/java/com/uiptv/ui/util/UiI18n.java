package com.uiptv.ui.util;

import com.uiptv.util.I18n;

import javafx.collections.ListChangeListener;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.PopupControl;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Font;
import javafx.stage.WindowEvent;

import java.util.List;
import java.util.regex.Pattern;

public final class UiI18n {
    private static final String LOCALE_FONT_FAMILY_KEY = "uiptv.locale.font.family";
    private static final String LOCALE_FONT_CHILDREN_KEY = "uiptv.locale.font.children";
    private static final Pattern INLINE_FONT_FAMILY_RULE_PATTERN = Pattern.compile("-fx-font-family\\s*:\\s*[^;]+;?");
    private static final String ARIAL_UNICODE_MS = "Arial Unicode MS";

    private UiI18n() {}

    public static void applySceneOrientation(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        scene.getRoot().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        applyLocaleTypography(scene.getRoot());
    }

    public static void preparePopupControl(PopupControl popupControl, Node ownerNode) {
        if (popupControl == null || ownerNode == null) return;
        popupControl.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> applyPopupSceneFormatting(popupControl, ownerNode));
        popupControl.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> applyPopupSceneFormatting(popupControl, ownerNode));
    }

    public static void applyLocaleTypography(Node node) {
        if (node == null) return;
        String preferredFamily = resolvePreferredFontFamily();
        if (!preferredFamily.isBlank()) applyPreferredFont(node, preferredFamily);
        else clearPreferredFont(node);
        if (node instanceof Parent parent) {
            attachTypographyListener(parent);
            for (Node child : parent.getChildrenUnmodifiable()) applyLocaleTypography(child);
        }
    }

    private static void applyPopupSceneFormatting(PopupControl popupControl, Node ownerNode) {
        if (popupControl == null || ownerNode == null) return;
        Scene popupScene = popupControl.getScene();
        if (popupScene == null || popupScene.getRoot() == null) return;
        Scene ownerScene = ownerNode.getScene();
        if (ownerScene != null) {
            String ownerRootStyle = ownerScene.getRoot() == null ? "" : ownerScene.getRoot().getStyle();
            popupControl.setStyle(ownerRootStyle);
            popupScene.getStylesheets().setAll(ownerScene.getStylesheets());
            if (ownerScene.getRoot() != null) {
                popupScene.getRoot().setStyle(ownerRootStyle);
                popupScene.getRoot().setNodeOrientation(ownerScene.getRoot().getNodeOrientation());
            }
        } else {
            popupScene.getRoot().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        }
    }

    private static void attachTypographyListener(Parent parent) {
        if (parent == null || Boolean.TRUE.equals(parent.getProperties().get(LOCALE_FONT_CHILDREN_KEY))) return;
        parent.getProperties().put(LOCALE_FONT_CHILDREN_KEY, Boolean.TRUE);
        parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) continue;
                for (Node node : change.getAddedSubList()) applyLocaleTypography(node);
            }
        });
    }

    private static void applyPreferredFont(Node node, String family) {
        if (node == null || family == null || family.isBlank() || node.styleProperty().isBound()) return;
        Object previousFamily = node.getProperties().get(LOCALE_FONT_FAMILY_KEY);
        if (family.equals(previousFamily)) return;
        node.getProperties().put(LOCALE_FONT_FAMILY_KEY, family);
        if (node instanceof Labeled labeled) labeled.setStyle(appendInlineFontFamily(labeled.getStyle(), family));
        else if (node instanceof TextInputControl textInputControl) textInputControl.setStyle(appendInlineFontFamily(textInputControl.getStyle(), family));
        else if (node instanceof Control control) control.setStyle(appendInlineFontFamily(control.getStyle(), family));
    }

    private static void clearPreferredFont(Node node) {
        if (node == null || node.styleProperty().isBound()) return;
        Object previousFamily = node.getProperties().remove(LOCALE_FONT_FAMILY_KEY);
        if (!(previousFamily instanceof String family) || family.isBlank()) return;
        if (node instanceof Labeled labeled) labeled.setStyle(removeInlineFontFamily(labeled.getStyle()));
        else if (node instanceof TextInputControl textInputControl) textInputControl.setStyle(removeInlineFontFamily(textInputControl.getStyle()));
        else if (node instanceof Control control) control.setStyle(removeInlineFontFamily(control.getStyle()));
    }

    static String appendInlineFontFamily(String existingStyle, String family) {
        String fontRule = "-fx-font-family: \"" + family.replace("\"", "") + "\";";
        if (existingStyle == null || existingStyle.isBlank()) return fontRule;
        if (existingStyle.contains("-fx-font-family")) return INLINE_FONT_FAMILY_RULE_PATTERN.matcher(existingStyle).replaceFirst(fontRule);
        return existingStyle.trim() + (existingStyle.trim().endsWith(";") ? " " : "; ") + fontRule;
    }

    static String removeInlineFontFamily(String existingStyle) {
        if (existingStyle == null || existingStyle.isBlank()) return "";
        String normalized = INLINE_FONT_FAMILY_RULE_PATTERN.matcher(existingStyle).replaceAll("").trim();
        normalized = normalizeInlineStyleSeparators(normalized);
        if (normalized.isBlank()) return "";
        return normalized.endsWith(";") ? normalized : normalized + ";";
    }

    private static String normalizeInlineStyleSeparators(String style) {
        if (style == null || style.isBlank()) return "";
        String[] parts = style.split(";");
        StringBuilder normalized = new StringBuilder();
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isEmpty()) continue;
            if (!normalized.isEmpty()) normalized.append("; ");
            normalized.append(trimmed);
        }
        return normalized.toString();
    }

    private static String resolvePreferredFontFamily() {
        String language = I18n.getCurrentLocale().getLanguage().toLowerCase(java.util.Locale.ROOT);
        return switch (language) {
            case "hi", "mr", "ne", "sa" -> firstAvailableFontFamily("Devanagari Sangam MN", "Kohinoor Devanagari", "Devanagari MT", "ITF Devanagari", "ITF Devanagari Marathi", "Shree Devanagari 714", "Noto Sans Devanagari", ARIAL_UNICODE_MS);
            case "bn" -> firstAvailableFontFamily("Bangla Sangam MN", "Bangla MN", "Kohinoor Bangla", "Noto Sans Bengali", ARIAL_UNICODE_MS);
            case "ur" -> firstAvailableFontFamily("Noto Nastaliq Urdu UI", "DecoType Nastaleeq Urdu Urdu UI", "Nafees Naskh", "Nafees Pakistani Naskh", "Geeza Pro", "Geeza Pro Interface", "Noto Naskh Arabic UI", "Noto Sans Arabic UI", ARIAL_UNICODE_MS);
            case "pa" -> firstAvailableFontFamily("Gurmukhi Sangam MN", "Gurmukhi MN", "Raavi", "Noto Sans Gurmukhi", ARIAL_UNICODE_MS);
            case "ta" -> firstAvailableFontFamily("Tamil Sangam MN", "Tamil MN", "Noto Sans Tamil", ARIAL_UNICODE_MS);
            case "te" -> firstAvailableFontFamily("Kohinoor Telugu", "Telugu Sangam MN", "Telugu MN", "Noto Sans Telugu", ARIAL_UNICODE_MS);
            case "ml" -> firstAvailableFontFamily("Malayalam Sangam MN", "Malayalam MN", "Noto Sans Malayalam", ARIAL_UNICODE_MS);
            default -> "";
        };
    }

    private static String firstAvailableFontFamily(String... candidates) {
        if (candidates == null || candidates.length == 0) return "";
        List<String> families = Font.getFamilies();
        for (String candidate : candidates) {
            if (candidate != null && families.contains(candidate)) return candidate;
        }
        return "";
    }
}
