package com.uiptv.ui;

import com.uiptv.db.AccountDb;
import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.util.AccountType;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.SegmentedProgressBar;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.CACHE_SUPPORTED;

public class ReloadCachePopup extends VBox {

    private final Stage stage;
    private final VBox accountsVBox = new VBox(5);
    private final VBox logVBox = new VBox(5);
    private final ScrollPane accountsScrollPane = new ScrollPane();
    private final ScrollPane logScrollPane = new ScrollPane(logVBox);
    private final SegmentedProgressBar progressBar = new SegmentedProgressBar();
    private final ProminentButton reloadButton = new ProminentButton("Reload Selected");
    private final ProgressIndicator loadingIndicator = createLoadingIndicator();
    private final CacheService cacheService = new CacheServiceImpl();
    private final List<CheckBox> checkBoxes = new ArrayList<>();

    public ReloadCachePopup(Stage stage) {
        this.stage = stage;
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefSize(1368, 720);
        getStylesheets().add(RootApplication.currentTheme);

        accountsVBox.setPadding(new Insets(10));
        List<Account> supportedAccounts = AccountDb.get().getAccounts().stream()
                .filter(account -> CACHE_SUPPORTED.contains(account.getType()))
                .collect(Collectors.toList());

        // Define the sort order for AccountType
        EnumMap<AccountType, Integer> order = new EnumMap<>(AccountType.class);
        order.put(AccountType.STALKER_PORTAL, 1);
        order.put(AccountType.XTREME_API, 2);
        order.put(AccountType.M3U8_LOCAL, 3);
        order.put(AccountType.M3U8_URL, 4);

        // Sort the accounts based on the defined order
        supportedAccounts.sort(Comparator.comparing(account -> order.getOrDefault(account.getType(), Integer.MAX_VALUE)));

        for (int i = 0; i < supportedAccounts.size(); i++) {
            Account account = supportedAccounts.get(i);
            CheckBox accountCheckBox = new CheckBox(account.getAccountName());
            accountCheckBox.setUserData(account);
            accountCheckBox.setMaxWidth(Double.MAX_VALUE);
            accountCheckBox.setPadding(new Insets(5));

            if (i % 2 == 0) {
                accountCheckBox.setStyle("-fx-background-color: derive(-fx-control-inner-background, -2%);");
            } else {
                accountCheckBox.setStyle("-fx-background-color: -fx-control-inner-background;");
            }
            accountsVBox.getChildren().add(accountCheckBox);
            checkBoxes.add(accountCheckBox);
        }

        MenuButton selectMenu = new MenuButton("Select by types");
        CheckMenuItem allItem = new CheckMenuItem("ALL");
        CheckMenuItem stalkerItem = new CheckMenuItem("Stalker Portal Accounts");
        CheckMenuItem xtremeItem = new CheckMenuItem("Xtreme Account");
        CheckMenuItem m3uLocalItem = new CheckMenuItem("M3U Local Playlist");
        CheckMenuItem m3uRemoteItem = new CheckMenuItem("M3U Remote Playlist");

        allItem.setOnAction(e -> {
            boolean selected = allItem.isSelected();
            checkBoxes.forEach(cb -> cb.setSelected(selected));
            stalkerItem.setSelected(selected);
            xtremeItem.setSelected(selected);
            m3uLocalItem.setSelected(selected);
            m3uRemoteItem.setSelected(selected);
        });

        stalkerItem.setOnAction(e -> updateCheckboxes(AccountType.STALKER_PORTAL, stalkerItem.isSelected()));
        xtremeItem.setOnAction(e -> updateCheckboxes(AccountType.XTREME_API, xtremeItem.isSelected()));
        m3uLocalItem.setOnAction(e -> updateCheckboxes(AccountType.M3U8_LOCAL, m3uLocalItem.isSelected()));
        m3uRemoteItem.setOnAction(e -> updateCheckboxes(AccountType.M3U8_URL, m3uRemoteItem.isSelected()));
        selectMenu.setPrefWidth(200);
        selectMenu.getItems().addAll(allItem, new SeparatorMenuItem(), stalkerItem, xtremeItem, m3uLocalItem, m3uRemoteItem);

        accountsScrollPane.setContent(accountsVBox);
        accountsScrollPane.setFitToWidth(true);
        accountsScrollPane.setMinHeight(250);
        VBox.setVgrow(accountsScrollPane, Priority.ALWAYS);

        logVBox.setPadding(new Insets(5));
        logScrollPane.setFitToWidth(true);
        logScrollPane.setMinHeight(250);
        VBox.setVgrow(logScrollPane, Priority.ALWAYS);
        logVBox.heightProperty().addListener((obs, oldVal, newVal) -> logScrollPane.setVvalue(1.0));


        reloadButton.setOnAction(event -> {
            logVBox.getChildren().clear();
            new Thread(this::reloadSelectedAccounts).start();
        });

        // Ensure proper layout when toggling visibility
        reloadButton.managedProperty().bind(reloadButton.visibleProperty());
        loadingIndicator.managedProperty().bind(loadingIndicator.visibleProperty());

        Button copyLogButton = new Button("Copy Log");
        copyLogButton.setOnAction(event -> {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            StringBuilder sb = new StringBuilder();
            for (Node node : logVBox.getChildren()) {
                if (node instanceof Text) {
                    sb.append(((Text) node).getText()).append("\n");
                } else if (node instanceof TextFlow) {
                    ((TextFlow) node).getChildren().forEach(t -> {
                        if (t instanceof Text) {
                            sb.append(((Text) t).getText());
                        }
                    });
                    sb.append("\n");
                }
            }
            content.putString(sb.toString());
            clipboard.setContent(content);
        });

        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonBox = new HBox(10, reloadButton, loadingIndicator, spacer, copyLogButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        getChildren().addAll(progressBar, selectMenu, accountsScrollPane, logScrollPane, buttonBox);
    }

    private void updateCheckboxes(AccountType type, boolean selected) {
        for (CheckBox cb : checkBoxes) {
            Account acc = (Account) cb.getUserData();
            if (acc.getType() == type) {
                cb.setSelected(selected);
            }
        }
    }

    private ProgressIndicator createLoadingIndicator() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(24, 24);
        indicator.setVisible(false);
        return indicator;
    }

    private void reloadSelectedAccounts() {
        Platform.runLater(() -> {
            reloadButton.setVisible(false);
            loadingIndicator.setVisible(true);
        });

        List<CheckBox> selectedAccounts = accountsVBox.getChildren().stream()
                .filter(node -> node instanceof CheckBox && ((CheckBox) node).isSelected())
                .map(node -> (CheckBox) node)
                .collect(Collectors.toList());

        int total = selectedAccounts.size();
        progressBar.setTotal(total);

        List<Account> processedAccounts = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            CheckBox checkBox = selectedAccounts.get(i);
            Account account = (Account) checkBox.getUserData();
            processedAccounts.add(account);
            boolean success = false;
            try {
                cacheService.reloadCache(account, this::logMessage);
                if (cacheService.getChannelCountForAccount(account.getDbId()) > 0) {
                    success = true;
                } else {
                    success = false;
                    logMessage("Warning: No channels found for " + account.getAccountName());
                }
            } catch (Exception e) {
                logMessage("Error reloading cache for " + account.getAccountName() + ": " + e.getMessage());
                success = false;
            }
            progressBar.updateSegment(i, success);
        }

        Platform.runLater(() -> {
            Text allDoneText = new Text("All done.");
            allDoneText.setStyle("-fx-font-weight: bold; -fx-fill: darkgreen;");
            logVBox.getChildren().add(allDoneText);
            System.out.print("\u0007"); // Beep
            reloadButton.setVisible(true);
            loadingIndicator.setVisible(false);

            List<Account> emptyAccounts = processedAccounts.stream()
                    .filter(a -> cacheService.getChannelCountForAccount(a.getDbId()) == 0)
                    .collect(Collectors.toList());

            if (!emptyAccounts.isEmpty()) {
                showDeleteEmptyAccountsPopup(emptyAccounts);
            }
        });
    }

    private void showDeleteEmptyAccountsPopup(List<Account> emptyAccounts) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Delete Empty Accounts");

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label warningLabel = new Label("The following accounts have 0 channels. Select the ones you want to delete.");
        warningLabel.setWrapText(true);
        warningLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        VBox accountsBox = new VBox(5);
        ScrollPane scrollPane = new ScrollPane(accountsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);

        CheckBox selectAll = new CheckBox("Select All");
        selectAll.setOnAction(e -> accountsBox.getChildren().forEach(node -> {
            if (node instanceof CheckBox) ((CheckBox) node).setSelected(selectAll.isSelected());
        }));

        for (Account account : emptyAccounts) {
            CheckBox cb = new CheckBox(account.getAccountName() + " (" + account.getType().getDisplay() + ")");
            cb.setUserData(account);
            accountsBox.getChildren().add(cb);
        }

        Button deleteButton = new Button("Delete Selected");
        deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteButton.setOnAction(e -> {
            List<Account> toDelete = accountsBox.getChildren().stream()
                    .filter(n -> n instanceof CheckBox && ((CheckBox) n).isSelected())
                    .map(n -> (Account) n.getUserData())
                    .collect(Collectors.toList());

            if (toDelete.isEmpty()) return;

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete " + toDelete.size() + " accounts?", ButtonType.YES, ButtonType.NO);
            if (RootApplication.currentTheme != null) {
                alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
            }
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    toDelete.forEach(a -> AccountService.getInstance().delete(a.getDbId()));
                    popupStage.close();
                }
            });
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> popupStage.close());

        HBox buttons = new HBox(10, deleteButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(warningLabel, selectAll, scrollPane, buttons);

        Scene scene = new Scene(root, 500, 500);
        if (RootApplication.currentTheme != null) {
            scene.getStylesheets().add(RootApplication.currentTheme);
        }
        popupStage.setScene(scene);
        popupStage.show();
    }

    public void logMessage(String message) {
        Platform.runLater(() -> {
            Text text = new Text(message);
            text.getStyleClass().add("log-text");
            logVBox.getChildren().add(text);
        });
    }
}
