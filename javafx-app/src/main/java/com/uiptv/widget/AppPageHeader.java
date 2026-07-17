package com.uiptv.widget;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public class AppPageHeader extends VBox {
    private static final double COMPACT_ENTER_WIDTH = 920;
    private static final double WIDE_ENTER_WIDTH = 1020;
    private final Label titleLabel = new Label();
    private final AppHeaderNavigation headerNavigation = new AppHeaderNavigation(titleLabel);
    private final HBox actions = new HBox(6);
    private final HBox wideHeaderRow = new HBox(12);
    private final Region wideSpacer = new Region();
    private final HBox compactTitleRow = new HBox(10);
    private boolean compactLayout;
    private boolean headerTitleVisible;

    public AppPageHeader(String title, Node actionNode) {
        this(title, actionNode == null ? null : List.of(actionNode));
    }

    public AppPageHeader(String title, List<? extends Node> actionNodes) {
        this(title, createActionContainer(actionNodes), true);
    }

    private AppPageHeader(String title, HBox actionContainer, boolean unused) {
        getStyleClass().add("uiptv-page-header");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeLayout(actions);
        UiRenderQuality.optimizeLayout(headerNavigation);
        UiRenderQuality.optimizeLayout(wideHeaderRow);
        UiRenderQuality.optimizeLayout(compactTitleRow);
        UiRenderQuality.optimizeTextNode(titleLabel);
        setFillWidth(true);
        setMaxWidth(Double.MAX_VALUE);

        titleLabel.setText(title == null ? "" : title);
        titleLabel.getStyleClass().add("uiptv-page-title");
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setPickOnBounds(false);
        updateHeaderTitleVisibility();

        actions.getStyleClass().add("uiptv-page-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        actions.setMaxWidth(Region.USE_PREF_SIZE);
        actions.setPickOnBounds(false);
        actions.getChildren().setAll(actionContainer.getChildren());

        wideHeaderRow.setAlignment(Pos.CENTER_LEFT);
        wideHeaderRow.setMaxWidth(Double.MAX_VALUE);
        compactTitleRow.setAlignment(Pos.CENTER_LEFT);
        compactTitleRow.setMaxWidth(Double.MAX_VALUE);

        applyLayout(false);
        widthProperty().addListener((_, _, width) -> applyLayoutForWidth(width.doubleValue()));
    }

    public void setTitle(String title) {
        titleLabel.setText(title == null ? "" : title);
        updateHeaderTitleVisibility();
    }

    public void setHeaderTitleVisible(boolean visible) {
        headerTitleVisible = visible;
        updateHeaderTitleVisibility();
    }

    public void setNavigationSelectionEnabled(boolean enabled) {
        headerNavigation.setSelectionEnabled(enabled);
    }

    private void applyLayoutForWidth(double width) {
        if (compactLayout) {
            if (width > WIDE_ENTER_WIDTH) {
                applyLayout(false);
            }
            return;
        }
        if (width < COMPACT_ENTER_WIDTH) {
            applyLayout(true);
        }
    }

    private void applyLayout(boolean compact) {
        if (compactLayout == compact && !getChildren().isEmpty()) {
            return;
        }

        wideHeaderRow.getChildren().clear();
        compactTitleRow.getChildren().clear();
        getChildren().clear();
        compactLayout = compact;
        headerNavigation.setCompact(compact);

        HBox.setHgrow(headerNavigation, Priority.NEVER);
        HBox.setHgrow(wideSpacer, Priority.ALWAYS);
        wideHeaderRow.getChildren().setAll(headerNavigation, wideSpacer, actions);
        getChildren().setAll(compactTitleRow, wideHeaderRow);
    }

    private void updateHeaderTitleVisibility() {
        boolean visible = headerTitleVisible && !titleLabel.getText().isBlank();
        titleLabel.setVisible(visible);
        titleLabel.setManaged(visible);
        headerNavigation.setTitleVisible(visible);
    }



    private static HBox createActionContainer(List<? extends Node> actionNodes) {
        HBox actionContainer = new HBox(6);
        UiRenderQuality.optimizeLayout(actionContainer);
        actionContainer.setAlignment(Pos.CENTER_RIGHT);
        if (actionNodes != null) {
            actionContainer.getChildren().setAll(actionNodes);
        }
        return actionContainer;
    }

}
