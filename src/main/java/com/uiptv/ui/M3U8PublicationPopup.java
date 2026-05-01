package com.uiptv.ui;

import com.uiptv.service.M3U8PublicationService;
import com.uiptv.util.I18n;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class M3U8PublicationPopup extends VBox {
    private static final String CUSTOMIZED_CHECKBOX_STYLE_CLASS = "published-m3u-customized-checkbox";
    private static final String SHOW_TRANSLATION_KEY = "commonShow";
    private final List<AccountNode> accountNodes = new ArrayList<>();
    private final M3U8PublicationService service = M3U8PublicationService.getInstance();
    private final M3U8PublicationService.PublicationSelections savedSelections;
    private final Map<M3U8PublicationService.CategorySelectionKey, Boolean> currentCategorySelections;
    private final Map<M3U8PublicationService.ChannelSelectionKey, Boolean> currentChannelSelections;

    public M3U8PublicationPopup(Stage stage) {
        this.savedSelections = service.getSelections();
        this.currentCategorySelections = new LinkedHashMap<>(savedSelections.categorySelections());
        this.currentChannelSelections = new LinkedHashMap<>(savedSelections.channelSelections());
        setPadding(new Insets(10));
        setSpacing(10);

        Label label = new Label(I18n.tr("autoSelectM3U8AccountsToPublish"));
        VBox content = new VBox(8);
        content.setFillWidth(true);

        for (M3U8PublicationService.PlaylistAccountSummary account : service.getAvailableAccounts()) {
            AccountNode node = new AccountNode(account);
            accountNodes.add(node);
            content.getChildren().add(node.container);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Button okButton = new Button(I18n.tr("autoOk"));
        okButton.setOnAction(e -> {
            service.saveSelections(buildSelectionsToSave());
            stage.close();
        });

        Button cancelButton = new Button(I18n.tr("autoCancel"));
        cancelButton.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, okButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(label, scrollPane, buttons);
    }

    private M3U8PublicationService.PublicationSelections buildSelectionsToSave() {
        Set<String> accountIds = new LinkedHashSet<>();
        for (AccountNode accountNode : accountNodes) {
            if (accountNode.baseSelection) {
                accountIds.add(accountNode.account.accountId());
            }
        }
        return new M3U8PublicationService.PublicationSelections(
                accountIds,
                new LinkedHashMap<>(currentCategorySelections),
                new LinkedHashMap<>(currentChannelSelections)
        );
    }

    private final class AccountNode {
        private final M3U8PublicationService.PlaylistAccountSummary account;
        private final CheckBox checkBox;
        private final Label loadingLabel = new Label(I18n.tr("commonLoading"));
        private final VBox childrenBox;
        private final VBox container;
        private final List<CategoryNode> categories = new ArrayList<>();
        private final Hyperlink toggleLink;
        private final boolean detailsSupported;
        private boolean baseSelection;
        private boolean loaded;
        private boolean loading;

        private AccountNode(M3U8PublicationService.PlaylistAccountSummary account) {
            this.account = account;
            this.detailsSupported = !service.isBookmarksPlaylistAccountId(account.accountId());
            this.baseSelection = savedSelections.accountIds().contains(account.accountId());
            this.checkBox = new CheckBox(account.accountName());
            this.childrenBox = new VBox(6);
            this.container = new VBox(6);
            this.toggleLink = detailsSupported ? new Hyperlink(I18n.tr(SHOW_TRANSLATION_KEY)) : null;

            checkBox.setSelected(baseSelection);
            checkBox.setOnAction(event -> {
                boolean wasDimmed = isDimmed();
                baseSelection = checkBox.isSelected();
                if (wasDimmed) {
                    clearAccountCustomizations();
                }
                refreshLoadedCategories();
                refreshSummaryState();
            });

            childrenBox.setPadding(new Insets(0, 0, 0, 20));
            childrenBox.setVisible(false);
            childrenBox.setManaged(false);
            container.getChildren().addAll(createRow(checkBox, toggleLink, this::toggleExpanded), childrenBox);
            refreshSummaryState();
        }

        private void toggleExpanded() {
            if (!detailsSupported) {
                return;
            }
            if (!loaded && !loading) {
                loadPlaylistAsync();
            }
            boolean show = !childrenBox.isVisible();
            childrenBox.setVisible(show);
            childrenBox.setManaged(show);
            toggleLink.setText(I18n.tr(show ? "commonHide" : SHOW_TRANSLATION_KEY));
        }

        private void loadPlaylistAsync() {
            loading = true;
            childrenBox.getChildren().setAll(loadingLabel);
            Task<M3U8PublicationService.PlaylistAccount> task = new Task<>() {
                @Override
                protected M3U8PublicationService.PlaylistAccount call() {
                    return service.getPlaylist(account.accountId());
                }
            };
            task.setOnSucceeded(event -> {
                loaded = true;
                loading = false;
                populateCategories(task.getValue());
            });
            task.setOnFailed(event -> {
                loaded = true;
                loading = false;
                childrenBox.getChildren().setAll(new Label(I18n.tr("autoFailed")));
            });
            Thread thread = new Thread(task, "m3u8-publication-loader-" + account.accountId());
            thread.setDaemon(true);
            thread.start();
        }

        private void populateCategories(M3U8PublicationService.PlaylistAccount playlist) {
            categories.clear();
            childrenBox.getChildren().clear();
            if (playlist == null || playlist.categories() == null || playlist.categories().isEmpty()) {
                childrenBox.getChildren().add(new Label(I18n.tr("commonNoResults")));
                refreshSummaryState();
                return;
            }
            for (M3U8PublicationService.PlaylistCategory category : playlist.categories()) {
                CategoryNode categoryNode = new CategoryNode(this, category);
                categories.add(categoryNode);
                childrenBox.getChildren().add(categoryNode.container);
            }
            refreshSummaryState();
        }

        private void refreshSummaryState() {
            if (!loaded || categories.isEmpty()) {
                applyCheckboxState(checkBox, baseSelection, baseSelection, hasAccountCustomization());
                return;
            }
            boolean anySelected = false;
            boolean allSelected = true;
            boolean customized = hasAccountCustomization();
            for (CategoryNode category : categories) {
                anySelected = anySelected || category.hasAnySelection();
                allSelected = allSelected && category.isFullySelected();
                customized = customized || category.isCustomized();
            }
            applyCheckboxState(checkBox, anySelected, allSelected, customized);
        }

        private boolean isDimmed() {
            return checkBox.getStyleClass().contains(CUSTOMIZED_CHECKBOX_STYLE_CLASS);
        }

        private void clearAccountCustomizations() {
            currentCategorySelections.keySet().removeIf(key -> account.accountId().equals(key.accountId()));
            currentChannelSelections.keySet().removeIf(key -> account.accountId().equals(key.accountId()));
        }

        private void refreshLoadedCategories() {
            for (CategoryNode category : categories) {
                category.refreshAllStates();
            }
        }

        private boolean hasAccountCustomization() {
            for (M3U8PublicationService.CategorySelectionKey key : currentCategorySelections.keySet()) {
                if (account.accountId().equals(key.accountId())) {
                    return true;
                }
            }
            for (M3U8PublicationService.ChannelSelectionKey key : currentChannelSelections.keySet()) {
                if (account.accountId().equals(key.accountId())) {
                    return true;
                }
            }
            return false;
        }
    }

    private final class CategoryNode {
        private final AccountNode parent;
        private final M3U8PublicationService.PlaylistCategory category;
        private final CheckBox checkBox;
        private final VBox childrenBox;
        private final VBox container;
        private final List<ChannelNode> channels = new ArrayList<>();
        private final Hyperlink toggleLink;

        private CategoryNode(AccountNode parent, M3U8PublicationService.PlaylistCategory category) {
            this.parent = parent;
            this.category = category;
            this.checkBox = new CheckBox(category.categoryName());
            this.childrenBox = new VBox(4);
            this.container = new VBox(4);
            this.toggleLink = new Hyperlink(I18n.tr(SHOW_TRANSLATION_KEY));

            for (M3U8PublicationService.PlaylistChannel channel : category.channels()) {
                ChannelNode channelNode = new ChannelNode(this, channel);
                channels.add(channelNode);
                childrenBox.getChildren().add(channelNode.row);
            }

            checkBox.setOnAction(event -> {
                boolean wasDimmed = isDimmed();
                boolean targetSelected = checkBox.isSelected();
                clearChannelSelections();
                if (wasDimmed) {
                    applyResetCategorySelection(targetSelected);
                } else {
                    updateCategorySelection(targetSelected);
                }
                refreshAllStates();
                parent.refreshSummaryState();
            });

            childrenBox.setPadding(new Insets(0, 0, 0, 20));
            childrenBox.setVisible(false);
            childrenBox.setManaged(false);
            container.getChildren().addAll(createRow(checkBox, toggleLink, this::toggleExpanded), childrenBox);
            refreshState();
        }

        private void toggleExpanded() {
            boolean show = !childrenBox.isVisible();
            childrenBox.setVisible(show);
            childrenBox.setManaged(show);
            toggleLink.setText(I18n.tr(show ? "commonHide" : SHOW_TRANSLATION_KEY));
        }

        private void refreshState() {
            boolean anySelected = false;
            boolean allSelected = !channels.isEmpty();
            boolean customized = false;
            for (ChannelNode channel : channels) {
                boolean effective = channel.isSelected();
                anySelected = anySelected || effective;
                allSelected = allSelected && effective;
                customized = customized || currentChannelSelections.containsKey(channel.selectionKey());
            }
            if (channels.isEmpty()) {
                anySelected = resolveBaseSelection();
                allSelected = anySelected;
            }
            applyCheckboxState(checkBox, anySelected, allSelected, customized);
        }

        private void refreshAllStates() {
            for (ChannelNode channel : channels) {
                channel.refreshState();
            }
            refreshState();
        }

        private void updateCategorySelection(boolean selected) {
            currentCategorySelections.put(selectionKey(), selected);
        }

        private void applyResetCategorySelection(boolean selected) {
            if (selected == parent.baseSelection) {
                currentCategorySelections.remove(selectionKey());
                return;
            }
            currentCategorySelections.put(selectionKey(), selected);
        }

        private void clearChannelSelections() {
            currentChannelSelections.keySet().removeIf(key ->
                    parent.account.accountId().equals(key.accountId())
                            && category.categoryName().equals(key.categoryName()));
        }

        private Boolean getExplicitSelection() {
            return currentCategorySelections.get(selectionKey());
        }

        private boolean resolveBaseSelection() {
            Boolean explicitSelection = getExplicitSelection();
            if (explicitSelection != null) {
                return explicitSelection;
            }
            return parent.baseSelection;
        }

        private boolean isFullySelected() {
            for (ChannelNode channel : channels) {
                if (!channel.isSelected()) {
                    return false;
                }
            }
            return !channels.isEmpty();
        }

        private boolean hasAnySelection() {
            for (ChannelNode channel : channels) {
                if (channel.isSelected()) {
                    return true;
                }
            }
            return channels.isEmpty() && resolveBaseSelection();
        }

        private boolean isCustomized() {
            if (currentCategorySelections.containsKey(selectionKey())) {
                return true;
            }
            for (ChannelNode channel : channels) {
                if (currentChannelSelections.containsKey(channel.selectionKey())) {
                    return true;
                }
            }
            return false;
        }

        private M3U8PublicationService.CategorySelectionKey selectionKey() {
            return new M3U8PublicationService.CategorySelectionKey(parent.account.accountId(), category.categoryName());
        }

        private boolean isDimmed() {
            return checkBox.getStyleClass().contains(CUSTOMIZED_CHECKBOX_STYLE_CLASS);
        }
    }

    private final class ChannelNode {
        private final CategoryNode parent;
        private final M3U8PublicationService.PlaylistChannel channel;
        private final CheckBox checkBox;
        private final HBox row;

        private ChannelNode(CategoryNode parent, M3U8PublicationService.PlaylistChannel channel) {
            this.parent = parent;
            this.channel = channel;
            this.checkBox = new CheckBox(channel.title());
            this.row = new HBox(checkBox);
            row.setAlignment(Pos.CENTER_LEFT);

            checkBox.setOnAction(event -> {
                currentChannelSelections.put(selectionKey(), checkBox.isSelected());
                parent.refreshState();
                parent.parent.refreshSummaryState();
            });

            refreshState();
        }

        private boolean isSelected() {
            Boolean explicitSelection = currentChannelSelections.get(selectionKey());
            if (explicitSelection != null) {
                return explicitSelection;
            }
            Boolean explicitCategorySelection = parent.getExplicitSelection();
            if (explicitCategorySelection != null) {
                return explicitCategorySelection;
            }
            return parent.parent.baseSelection;
        }

        private void refreshState() {
            checkBox.setSelected(isSelected());
        }

        private M3U8PublicationService.ChannelSelectionKey selectionKey() {
            return new M3U8PublicationService.ChannelSelectionKey(
                    parent.parent.account.accountId(),
                    parent.category.categoryName(),
                    channel.channelId()
            );
        }
    }

    private HBox createRow(CheckBox checkBox, Hyperlink toggleLink, Runnable onToggle) {
        checkBox.setMaxWidth(Double.MAX_VALUE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = toggleLink == null ? new HBox(8, checkBox, spacer) : new HBox(8, checkBox, spacer, toggleLink);
        if (toggleLink != null) {
            toggleLink.setOnAction(event -> onToggle.run());
        }
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    List<String> accountNamesForTest() {
        return accountNodes.stream().map(node -> node.account.accountName()).toList();
    }

    boolean hasDetailsToggleForTest(String accountId) {
        return accountNodes.stream()
                .filter(node -> node.account.accountId().equals(accountId))
                .findFirst()
                .map(node -> node.toggleLink != null)
                .orElse(false);
    }

    boolean isAccountSelectedForTest(String accountId) {
        return accountNodes.stream()
                .filter(node -> node.account.accountId().equals(accountId))
                .findFirst()
                .map(node -> node.checkBox.isSelected())
                .orElse(false);
    }

    void setAccountSelectedForTest(String accountId, boolean selected) {
        accountNodes.stream()
                .filter(node -> node.account.accountId().equals(accountId))
                .findFirst()
                .ifPresent(node -> {
                    if (node.checkBox.isSelected() != selected) {
                        node.checkBox.fire();
                    }
                });
    }

    private void applyCheckboxState(CheckBox checkBox, boolean anySelected, boolean allSelected, boolean customized) {
        checkBox.getStyleClass().remove(CUSTOMIZED_CHECKBOX_STYLE_CLASS);
        if (!anySelected) {
            checkBox.setAllowIndeterminate(false);
            checkBox.setIndeterminate(false);
            checkBox.setSelected(false);
            return;
        }
        if (customized || !allSelected) {
            checkBox.getStyleClass().add(CUSTOMIZED_CHECKBOX_STYLE_CLASS);
        }
        checkBox.setSelected(true);
    }
}
