package com.uiptv.ui;

import com.uiptv.model.AccountInfo;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class ManageAccountInfoPane extends BorderPane {
    private static final String PROFILE_DATA_TITLE_KEY = "manageAccountInfoProfileJson";
    private static final double PROFILE_POPUP_WIDTH = 840;
    private static final double PROFILE_POPUP_HEIGHT = 560;

    private final Label compactAccountInfoExpireDate = new Label();
    private final Region compactAccountInfoExpiryIndicator = new Region();
    private final Button accountInfoProfileViewButton = new Button(I18n.tr(PROFILE_DATA_TITLE_KEY));

    private String accountInfoProfileRawJson;
    private String accountInfoProfileFormattedText = "";
    private boolean accountInfoHasProfileJson;
    private boolean accountInfoHasSummary;

    public ManageAccountInfoPane() {
        buildPane();
    }

    public boolean hasProfileJson() {
        return accountInfoHasProfileJson;
    }

    public boolean hasSummary() {
        return accountInfoHasSummary;
    }

    public void apply(AccountInfo info) {
        if (info == null) {
            clear();
            return;
        }

        String profileJson = safeText(info.getProfileJson());
        accountInfoHasProfileJson = isNotBlank(profileJson);
        String rawExpire = safeText(info.getExpireDate());
        boolean missingExpiry = isBlank(rawExpire) || rawExpire.startsWith("0000-00-00");
        accountInfoHasSummary = accountInfoHasProfileJson || !missingExpiry;
        if (!accountInfoHasSummary) {
            clear();
            return;
        }

        if (missingExpiry) {
            setExpiryDisplay("Unknown");
            applyExpiryIndicator(AccountInfoUiUtil.colorForExpiry(AccountInfoUiUtil.ExpiryState.UNKNOWN), true);
        } else {
            AccountInfoUiUtil.ParsedDate parsedExpire = AccountInfoUiUtil.parseDateValue(rawExpire);
            String displayExpire = AccountInfoUiUtil.formatDate(rawExpire);
            setExpiryDisplay(displayExpire);
            updateExpiryIndicator(parsedExpire.instant(), isNotBlank(displayExpire));
        }
        setAccountInfoProfileJson(profileJson);
    }

    public void clear() {
        accountInfoHasProfileJson = false;
        accountInfoHasSummary = false;
        compactAccountInfoExpireDate.setText("");
        setAccountInfoProfileJson("");
        updateExpiryIndicator(null, false);
    }

    private void buildPane() {
        compactAccountInfoExpiryIndicator.setMinSize(9, 9);
        compactAccountInfoExpiryIndicator.setPrefSize(9, 9);
        compactAccountInfoExpiryIndicator.setMaxSize(9, 9);
        compactAccountInfoExpiryIndicator.setStyle("-fx-background-radius: 6px;");

        accountInfoProfileViewButton.getStyleClass().add("manage-account-info-profile-button");
        accountInfoProfileViewButton.setMinWidth(Region.USE_PREF_SIZE);
        accountInfoProfileViewButton.setVisible(false);
        accountInfoProfileViewButton.setManaged(false);
        accountInfoProfileViewButton.setOnAction(event -> showProfileDataPopup());

        Label compactLabel = new Label(I18n.tr("manageAccountInfoExpireDate"));
        compactLabel.getStyleClass().add("manage-account-info-compact-label");
        compactAccountInfoExpireDate.getStyleClass().add("manage-account-info-compact-value");
        compactAccountInfoExpireDate.setMinWidth(0);
        compactAccountInfoExpireDate.setMaxWidth(Double.MAX_VALUE);
        compactAccountInfoExpireDate.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox compactExpiry = new HBox(6, compactAccountInfoExpiryIndicator, compactLabel, compactAccountInfoExpireDate);
        compactExpiry.setAlignment(Pos.CENTER_LEFT);
        compactExpiry.setMinWidth(0);
        compactExpiry.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(compactAccountInfoExpireDate, Priority.ALWAYS);
        HBox.setHgrow(compactExpiry, Priority.ALWAYS);

        HBox compactContent = new HBox(8, compactExpiry, accountInfoProfileViewButton);
        compactContent.getStyleClass().add("manage-account-info-compact");
        compactContent.setAlignment(Pos.CENTER_LEFT);
        compactContent.setMinWidth(0);
        compactContent.setMaxWidth(Double.MAX_VALUE);

        setCenter(compactContent);
        setPadding(new Insets(6, 8, 6, 8));
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("manage-account-info-card");
    }

    private void setExpiryDisplay(String value) {
        compactAccountInfoExpireDate.setText(safeText(value));
    }

    private void setAccountInfoProfileJson(String value) {
        String safeValue = safeText(value);
        boolean visible = isNotBlank(safeValue);
        accountInfoProfileRawJson = safeValue;
        accountInfoProfileFormattedText = visible ? formatProfileJsonForDisplay(safeValue) : "";
        accountInfoProfileViewButton.setVisible(visible);
        accountInfoProfileViewButton.setManaged(visible);
    }

    private void showProfileDataPopup() {
        String displayText = profileDataText();
        if (isBlank(displayText)) {
            return;
        }

        Stage stage = new Stage();
        Window owner = resolveOwnerWindow();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.setTitle(I18n.tr(PROFILE_DATA_TITLE_KEY));

        TextArea profileTextArea = createProfileTextArea(displayText);
        VBox.setVgrow(profileTextArea, Priority.ALWAYS);

        Button copyButton = new Button(I18n.tr("autoCopy"));
        Button closeButton = new Button(I18n.tr("commonClose"));
        copyButton.getStyleClass().add("account-info-popup-copy-button");
        closeButton.getStyleClass().add("account-info-popup-close-button");
        copyButton.setOnAction(event -> copyToClipboard(profileTextArea.getText()));
        closeButton.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, spacer, copyButton, closeButton);
        actions.getStyleClass().add("account-info-popup-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        Label title = new Label(I18n.tr(PROFILE_DATA_TITLE_KEY));
        title.getStyleClass().add("account-info-popup-title");
        Label subtitle = new Label(I18n.tr("manageAccountInfoDescription"));
        subtitle.getStyleClass().add("account-info-popup-subtitle");
        subtitle.setWrapText(true);

        VBox card = new VBox(12, title, subtitle, profileTextArea, actions);
        card.getStyleClass().add("account-info-popup-card");
        card.setFillWidth(true);
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        VBox root = new VBox(card);
        root.getStyleClass().add("account-info-popup-root");
        root.setPadding(new Insets(16));
        VBox.setVgrow(card, Priority.ALWAYS);

        Scene scene = new Scene(root, PROFILE_POPUP_WIDTH, PROFILE_POPUP_HEIGHT);
        UiI18n.applySceneOrientation(scene);
        applyOwnerStylesheets(scene, owner);
        stage.setScene(scene);
        stage.showAndWait();
    }

    TextArea createProfileTextArea(String displayText) {
        TextArea profileTextArea = new TextArea(displayText);
        profileTextArea.getStyleClass().add("account-info-profile-text-area");
        profileTextArea.setEditable(false);
        profileTextArea.setWrapText(false);
        profileTextArea.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return profileTextArea;
    }

    private String profileDataText() {
        if (isNotBlank(accountInfoProfileFormattedText)) {
            return accountInfoProfileFormattedText;
        }
        return accountInfoProfileRawJson == null ? "" : accountInfoProfileRawJson;
    }

    private Window resolveOwnerWindow() {
        if (getScene() != null) {
            return getScene().getWindow();
        }
        return RootApplication.getPrimaryStage();
    }

    private void applyOwnerStylesheets(Scene scene, Window owner) {
        if (owner instanceof Stage ownerStage && ownerStage.getScene() != null) {
            scene.getStylesheets().addAll(ownerStage.getScene().getStylesheets());
            return;
        }
        if (getScene() != null) {
            scene.getStylesheets().addAll(getScene().getStylesheets());
            return;
        }
        if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
    }

    private void copyToClipboard(String text) {
        if (isBlank(text)) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String formatProfileJsonForDisplay(String rawJson) {
        if (isBlank(rawJson)) {
            return "";
        }
        String trimmed = rawJson.trim();
        try {
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                List<String> lines = new ArrayList<>();
                flattenJson("", array, lines);
                return String.join("\n", lines);
            }
            JSONObject obj = new JSONObject(trimmed);
            List<String> lines = new ArrayList<>();
            flattenJson("", obj, lines);
            return String.join("\n", lines);
        } catch (Exception _) {
            return trimmed;
        }
    }

    private void flattenJson(String path, Object value, List<String> lines) {
        if (value == null || value == JSONObject.NULL) {
            addLine(path, "null", lines);
            return;
        }
        if (value instanceof JSONObject obj) {
            for (String key : obj.keySet().stream().sorted().toList()) {
                String newPath = path.isBlank() ? key : path + "." + key;
                flattenJson(newPath, obj.opt(key), lines);
            }
            return;
        }
        if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                String newPath = path + "[" + i + "]";
                flattenJson(newPath, array.opt(i), lines);
            }
            return;
        }
        addLine(path, String.valueOf(value), lines);
    }

    private void addLine(String key, String value, List<String> lines) {
        if (isBlank(key)) {
            return;
        }
        lines.add(key + ": " + formatProfileValue(key, value));
    }

    private String formatProfileValue(String key, String value) {
        if (isBlank(value)) {
            return value;
        }
        if (!looksLikeDateValue(key, value)) {
            return value;
        }
        String formatted = AccountInfoUiUtil.formatDate(value);
        return isNotBlank(formatted) ? formatted : value;
    }

    private boolean looksLikeDateValue(String key, String value) {
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalizedKey.contains("date")
                || normalizedKey.contains("time")
                || normalizedKey.contains("created")
                || normalizedKey.contains("updated")
                || normalizedKey.contains("watchdog")
                || normalizedKey.contains("active")
                || normalizedKey.contains("start")
                || normalizedKey.contains("expire")
                || normalizedKey.endsWith("at")) {
            return true;
        }
        AccountInfoUiUtil.ParsedDate parsed = AccountInfoUiUtil.parseDateValue(value);
        return isNotBlank(parsed.display()) && !parsed.display().equals(value.trim());
    }

    private void updateExpiryIndicator(Instant instant, boolean hasValue) {
        if (!hasValue || instant == null) {
            applyExpiryIndicator(AccountInfoUiUtil.colorForExpiry(AccountInfoUiUtil.ExpiryState.UNKNOWN), false);
            return;
        }
        AccountInfoUiUtil.ExpiryState state = AccountInfoUiUtil.resolveExpiryState(instant);
        String color = AccountInfoUiUtil.colorForExpiry(state);
        applyExpiryIndicator(color, true);
    }

    private void applyExpiryIndicator(String color, boolean visible) {
        AccountInfoUiUtil.applyIndicator(compactAccountInfoExpiryIndicator, color, visible);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

}
