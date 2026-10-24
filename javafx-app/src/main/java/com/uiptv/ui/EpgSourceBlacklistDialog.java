package com.uiptv.ui;

import com.uiptv.application.EpgSourceBlacklistService;
import com.uiptv.db.EpgSourceBlacklistDb;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Locale;

/** Small manager for XMLTV URLs which should never be contacted. */
final class EpgSourceBlacklistDialog {
    private static final double DIALOG_WIDTH = 860;
    private static final double DIALOG_HEIGHT = 620;

    private EpgSourceBlacklistDialog() { }

    static void open(Control owner) {
        Stage stage = new Stage();
        Window window = owner.getScene() == null ? null : owner.getScene().getWindow();
        if (window != null) stage.initOwner(window);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(I18n.tr("epgBlacklistManager"));

        TextField source = new TextField();
        source.setPromptText(I18n.tr("epgBlacklistSourcePrompt"));
        Button add = new Button(I18n.tr("commonAdd"));
        TextField search = new TextField();
        search.setPromptText(I18n.tr("commonSearch"));
        ObservableList<EpgSourceBlacklistDb.Entry> allEntries = FXCollections.observableArrayList();
        FilteredList<EpgSourceBlacklistDb.Entry> filteredEntries = new FilteredList<>(allEntries);
        search.textProperty().addListener((_, _, query) ->
                filteredEntries.setPredicate(entry -> matchesSearch(entry, query)));
        ListView<EpgSourceBlacklistDb.Entry> entries = new ListView<>(filteredEntries);
        entries.setCellFactory(_ -> new ListCell<>() {
            @Override protected void updateItem(EpgSourceBlacklistDb.Entry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label value = new Label(item.source());
                value.setWrapText(true);
                value.setMinWidth(0);
                value.setMaxWidth(Double.MAX_VALUE);
                MenuItem copyMenuItem = new MenuItem(I18n.tr("autoCopy"));
                copyMenuItem.setOnAction(_ -> copyToClipboard(item.source()));
                value.setContextMenu(new ContextMenu(copyMenuItem));
                Button copy = new Button(I18n.tr("autoCopy"));
                copy.setOnAction(_ -> copyToClipboard(item.source()));
                Button remove = new Button(I18n.tr("epgBlacklistRemove"));
                remove.setOnAction(_ -> {
                    EpgSourceBlacklistService.remove(item.sourceId());
                    load(allEntries);
                });
                HBox row = new HBox(8, value, copy, remove);
                HBox.setHgrow(value, Priority.ALWAYS);
                row.setFillHeight(true);
                setGraphic(row);
            }
        });
        add.setOnAction(_ -> {
            String value = source.getText().strip();
            if (value.isBlank()) return;
            try {
                EpgSourceBlacklistService.add(value);
                source.clear();
                load(allEntries);
            } catch (RuntimeException error) {
                new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK).showAndWait();
            }
        });
        HBox addRow = new HBox(8, source, add);
        HBox.setHgrow(source, Priority.ALWAYS);
        VBox root = new VBox(10, new Label(I18n.tr("epgBlacklistHelp")), search, addRow, entries);
        root.setPadding(new Insets(16));
        VBox.setVgrow(entries, Priority.ALWAYS);
        Scene scene = new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
        RootApplication.applyThemeToPopupScene(scene);
        stage.setScene(scene);
        stage.show();
        load(allEntries);
    }

    static boolean matchesSearch(EpgSourceBlacklistDb.Entry entry, String query) {
        if (entry == null) return false;
        String normalizedQuery = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return normalizedQuery.isBlank()
                || entry.source().toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private static void copyToClipboard(String source) {
        ClipboardContent content = new ClipboardContent();
        content.putString(source);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static void load(ObservableList<EpgSourceBlacklistDb.Entry> entries) {
        Thread.ofVirtual().start(() -> {
            var values = EpgSourceBlacklistService.list();
            Platform.runLater(() -> entries.setAll(values));
        });
    }
}
