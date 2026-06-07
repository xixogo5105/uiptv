package com.uiptv.widget;

import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.List;

public class AppPageHeader extends VBox {
    private static final String LEGACY_SEARCH_CLEAR_INSTALLED_KEY =
            AppPageHeader.class.getName() + ".legacySearchClearInstalled";
    private static final double COMPACT_ENTER_WIDTH = 920;
    private static final double WIDE_ENTER_WIDTH = 1020;
    private static final double WIDE_SEARCH_MIN_WIDTH = 140;
    private static final double WIDE_SEARCH_WIDTH = 420;
    private static final double SEARCH_BUTTON_SIZE = 34;
    private static final String ICON_SEARCH = "M9.5 3C5.91 3 3 5.91 3 9.5S5.91 16 9.5 16C11.11 16 12.59 15.41 13.73 14.43L18.29 18.99 19.7 17.58 15.14 13.02C15.97 11.95 16 10.76 16 9.5 16 5.91 13.09 3 9.5 3ZM9.5 5C11.99 5 14 7.01 14 9.5S11.99 14 9.5 14 5 11.99 5 9.5 7.01 5 9.5 5Z";

    private final Label titleLabel = new Label();
    private final AppHeaderNavigation headerNavigation = new AppHeaderNavigation(titleLabel);
    private final TextField searchField;
    private final Button searchToggleButton;
    private final HBox actions = new HBox(6);
    private final HBox wideHeaderRow = new HBox(12);
    private final Region wideSpacer = new Region();
    private final HBox compactTitleRow = new HBox(10);
    private boolean compactLayout;
    private boolean headerTitleVisible;
    private boolean searchFieldVisible;

    public AppPageHeader(String title, TextField searchField, List<? extends Node> actionNodes) {
        this(title, searchField, createActionContainer(actionNodes));
    }

    public AppPageHeader(String title, Node actionNode) {
        this(title, null, actionNode);
    }

    public AppPageHeader(String title, TextField searchField, Node actionNode) {
        this.searchField = searchField;
        getStyleClass().add("uiptv-page-header");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeLayout(actions);
        UiRenderQuality.optimizeLayout(headerNavigation);
        UiRenderQuality.optimizeLayout(wideHeaderRow);
        UiRenderQuality.optimizeLayout(compactTitleRow);
        UiRenderQuality.optimizeTextNode(titleLabel);
        if (this.searchField != null) {
            UiRenderQuality.optimizeTextNode(this.searchField);
        }
        searchToggleButton = this.searchField == null ? null : createSearchToggleButton();
        setFillWidth(true);
        setMaxWidth(Double.MAX_VALUE);

        titleLabel.setText(title == null ? "" : title);
        titleLabel.getStyleClass().add("uiptv-page-title");
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setPickOnBounds(false);
        updateHeaderTitleVisibility();

        if (this.searchField != null) {
            this.searchField.getStyleClass().add("uiptv-page-search-field");
            this.searchField.setMinWidth(WIDE_SEARCH_MIN_WIDTH);
            this.searchField.setPrefWidth(WIDE_SEARCH_WIDTH);
            this.searchField.setMaxWidth(WIDE_SEARCH_WIDTH);
            installLegacySearchClearBehavior(this.searchField);
            setSearchFieldVisible(false);
            headerNavigation.setTrailingAction(searchToggleButton);
        }

        actions.getStyleClass().add("uiptv-page-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        actions.setMaxWidth(Region.USE_PREF_SIZE);
        actions.setPickOnBounds(false);
        if (actionNode != null) {
            actions.getChildren().setAll(actionNode);
        }

        wideHeaderRow.setAlignment(Pos.CENTER_LEFT);
        wideHeaderRow.setMaxWidth(Double.MAX_VALUE);
        compactTitleRow.setAlignment(Pos.CENTER_LEFT);
        compactTitleRow.setMaxWidth(Double.MAX_VALUE);

        applyLayout(false);
        widthProperty().addListener((_, _, width) -> applyLayoutForWidth(width.doubleValue()));
    }

    public TextField getSearchField() {
        return searchField;
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
        applyLayout(compact, false);
    }

    private void applyLayout(boolean compact, boolean force) {
        if (!force && compactLayout == compact && !getChildren().isEmpty()) {
            return;
        }

        wideHeaderRow.getChildren().clear();
        compactTitleRow.getChildren().clear();
        getChildren().clear();
        compactLayout = compact;
        headerNavigation.setCompact(compact);

        if (compact) {
            HBox.setHgrow(headerNavigation, Priority.NEVER);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            compactTitleRow.getChildren().setAll(headerNavigation, spacer, actions);
            if (isSearchFieldVisible()) {
                searchField.setMinWidth(0);
                searchField.setMaxWidth(Double.MAX_VALUE);
                getChildren().setAll(compactTitleRow, searchField);
            } else {
                getChildren().setAll(compactTitleRow);
            }
            return;
        }

        HBox.setHgrow(headerNavigation, Priority.NEVER);
        HBox.setHgrow(wideSpacer, Priority.ALWAYS);
        if (!isSearchFieldVisible()) {
            wideHeaderRow.getChildren().setAll(headerNavigation, wideSpacer, actions);
        } else {
            searchField.setMinWidth(WIDE_SEARCH_MIN_WIDTH);
            searchField.setMaxWidth(WIDE_SEARCH_WIDTH);
            HBox.setHgrow(searchField, Priority.SOMETIMES);
            wideHeaderRow.getChildren().setAll(headerNavigation, searchField, wideSpacer, actions);
        }
        getChildren().setAll(wideHeaderRow);
    }

    private void updateHeaderTitleVisibility() {
        boolean visible = headerTitleVisible && !titleLabel.getText().isBlank();
        titleLabel.setVisible(visible);
        titleLabel.setManaged(visible);
        headerNavigation.setTitleVisible(visible);
    }

    private Button createSearchToggleButton() {
        Button button = new Button();
        button.getStyleClass().addAll("app-header-nav-button", "app-header-nav-button-icon-only", "app-header-search-toggle");
        button.setAccessibleText(I18n.tr("commonSearch"));
        button.setTooltip(AppNavigationPane.createImmediateTooltip(I18n.tr("commonSearch")));
        button.setGraphic(createSearchIcon());
        button.setFocusTraversable(true);
        button.setMinWidth(SEARCH_BUTTON_SIZE);
        button.setPrefWidth(SEARCH_BUTTON_SIZE);
        button.setMaxWidth(SEARCH_BUTTON_SIZE);
        button.setMinHeight(SEARCH_BUTTON_SIZE);
        button.setPrefHeight(SEARCH_BUTTON_SIZE);
        button.setOnAction(_ -> toggleSearchField());
        return button;
    }

    private Node createSearchIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent(ICON_SEARCH);
        icon.getStyleClass().add("app-header-nav-icon");
        UiRenderQuality.optimizeTextNode(icon);
        return icon;
    }

    private void toggleSearchField() {
        setSearchFieldVisible(!isSearchFieldVisible());
        applyLayout(compactLayout, true);
        if (isSearchFieldVisible()) {
            Platform.runLater(searchField::requestFocus);
        }
    }

    private boolean isSearchFieldVisible() {
        return searchField != null && searchFieldVisible;
    }

    private void setSearchFieldVisible(boolean visible) {
        if (searchField == null) {
            return;
        }
        boolean wasVisible = searchFieldVisible;
        String searchText = searchField.getText();
        if (!visible && wasVisible && searchText != null && !searchText.isEmpty()) {
            searchField.clear();
        }
        searchFieldVisible = visible;
        searchField.setVisible(visible);
        searchField.setManaged(visible);
        if (searchToggleButton != null) {
            boolean hasActiveStyle = searchToggleButton.getStyleClass().contains("app-header-nav-button-active");
            if (visible && !hasActiveStyle) {
                searchToggleButton.getStyleClass().add("app-header-nav-button-active");
            } else if (!visible && hasActiveStyle) {
                searchToggleButton.getStyleClass().removeAll("app-header-nav-button-active");
            }
        }
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

    private static void installLegacySearchClearBehavior(TextField field) {
        if (field == null || Boolean.TRUE.equals(field.getProperties().get(LEGACY_SEARCH_CLEAR_INSTALLED_KEY))) {
            return;
        }
        EventHandler<? super MouseEvent> existingHandler = field.getOnMousePressed();
        field.getProperties().put(LEGACY_SEARCH_CLEAR_INSTALLED_KEY, Boolean.TRUE);
        field.setOnMousePressed(event -> {
            if (existingHandler != null) {
                existingHandler.handle(event);
            }
            if (!event.isConsumed()) {
                field.clear();
            }
        });
    }
}
