package com.uiptv.ui;

import com.uiptv.model.AccountInfo;
import com.uiptv.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class ManageAccountInfoPane extends BorderPane {
    private static final String STYLE_CLASS_DIM_LABEL = "dim-label";
    private static final String PROFILE_DATA_TITLE = "Profile data";

    private final Label accountInfoExpireDate = new Label();
    private final Label accountInfoStatus = new Label();
    private final Label accountInfoBalance = new Label();
    private final Label accountInfoTariffName = new Label();
    private final Label accountInfoTariffPlan = new Label();
    private final Label accountInfoDefaultTimezone = new Label();
    private final Label accountInfoProfileTitleLabel = new Label(PROFILE_DATA_TITLE);
    private final VBox accountInfoProfileContainer = new VBox(6);
    private final BorderPane accountInfoProfileBox = new BorderPane();
    private final VBox accountInfoProfileLines = new VBox(4);
    private final StackPane accountInfoProfileToggle = new StackPane();
    private final javafx.scene.shape.SVGPath accountInfoProfileToggleIcon = new javafx.scene.shape.SVGPath();
    private final Region accountInfoStatusIndicator = new Region();
    private final Region accountInfoExpiryIndicator = new Region();
    private final javafx.scene.control.Button accountInfoProfileCopyButton = new javafx.scene.control.Button(I18n.tr("autoCopy"));

    private String accountInfoProfileRawJson;
    private boolean accountInfoHasProfileJson;
    private AccountInfoRow accountInfoExpireDateRow;
    private AccountInfoRow accountInfoStatusRow;
    private AccountInfoRow accountInfoBalanceRow;
    private AccountInfoRow accountInfoTariffNameRow;
    private AccountInfoRow accountInfoTariffPlanRow;
    private AccountInfoRow accountInfoDefaultTimezoneRow;

    public ManageAccountInfoPane() {
        configureProfileJsonArea();
        buildPane();
    }

    public boolean hasProfileJson() {
        return accountInfoHasProfileJson;
    }

    public void apply(AccountInfo info) {
        String profileJson = info != null ? safeText(info.getProfileJson()) : "";
        accountInfoHasProfileJson = isNotBlank(profileJson);
        if (!accountInfoHasProfileJson) {
            clear();
            return;
        }

        String rawExpire = info != null ? safeText(info.getExpireDate()) : "";
        boolean missingExpiry = isBlank(rawExpire) || rawExpire.startsWith("0000-00-00");
        if (missingExpiry) {
            setAccountInfoValue(accountInfoExpireDateRow, accountInfoExpireDate, "Unknown");
            AccountInfoUiUtil.applyIndicator(accountInfoExpiryIndicator, AccountInfoUiUtil.colorForExpiry(AccountInfoUiUtil.ExpiryState.UNKNOWN), true);
        } else {
            AccountInfoUiUtil.ParsedDate parsedExpire = AccountInfoUiUtil.parseDateValue(rawExpire);
            String displayExpire = AccountInfoUiUtil.formatDate(rawExpire);
            setAccountInfoValue(accountInfoExpireDateRow, accountInfoExpireDate, displayExpire);
            updateExpiryIndicator(parsedExpire.instant(), isNotBlank(displayExpire));
        }

        com.uiptv.model.AccountStatus status = info != null ? info.getAccountStatus() : null;
        String statusText = safeStatus(status);
        setAccountInfoValue(accountInfoStatusRow, accountInfoStatus, statusText);
        updateStatusIndicator(statusText);

        setAccountInfoValue(accountInfoBalanceRow, accountInfoBalance, info != null ? safeText(info.getAccountBalance()) : "");
        setAccountInfoValue(accountInfoTariffNameRow, accountInfoTariffName, info != null ? safeText(info.getTariffName()) : "");
        setAccountInfoValue(accountInfoTariffPlanRow, accountInfoTariffPlan, info != null ? safeText(info.getTariffPlan()) : "");
        setAccountInfoValue(accountInfoDefaultTimezoneRow, accountInfoDefaultTimezone, info != null ? safeText(info.getDefaultTimezone()) : "");
        setAccountInfoProfileJson(profileJson);
    }

    public void clear() {
        accountInfoHasProfileJson = false;
        setAccountInfoValue(accountInfoExpireDateRow, accountInfoExpireDate, "");
        setAccountInfoValue(accountInfoStatusRow, accountInfoStatus, "");
        setAccountInfoValue(accountInfoBalanceRow, accountInfoBalance, "");
        setAccountInfoValue(accountInfoTariffNameRow, accountInfoTariffName, "");
        setAccountInfoValue(accountInfoTariffPlanRow, accountInfoTariffPlan, "");
        setAccountInfoValue(accountInfoDefaultTimezoneRow, accountInfoDefaultTimezone, "");
        setAccountInfoProfileJson("");
        updateStatusIndicator("");
        updateExpiryIndicator(null, false);
    }

    private void buildPane() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints labelColumn = new ColumnConstraints();
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);

        accountInfoStatusIndicator.setMinSize(10, 10);
        accountInfoStatusIndicator.setPrefSize(10, 10);
        accountInfoStatusIndicator.setMaxSize(10, 10);
        accountInfoStatusIndicator.setStyle("-fx-background-radius: 6px;");

        accountInfoExpiryIndicator.setMinSize(10, 10);
        accountInfoExpiryIndicator.setPrefSize(10, 10);
        accountInfoExpiryIndicator.setMaxSize(10, 10);
        accountInfoExpiryIndicator.setStyle("-fx-background-radius: 6px;");

        HBox expiryValue = new HBox(6, accountInfoExpiryIndicator, accountInfoExpireDate);
        expiryValue.setAlignment(Pos.CENTER_LEFT);

        HBox statusValue = new HBox(6, accountInfoStatusIndicator, accountInfoStatus);
        statusValue.setAlignment(Pos.CENTER_LEFT);

        accountInfoExpireDateRow = addAccountInfoRow(grid, 0, "manageAccountInfoExpireDate", expiryValue);
        accountInfoStatusRow = addAccountInfoRow(grid, 1, "manageAccountInfoStatus", statusValue);
        accountInfoBalanceRow = addAccountInfoRow(grid, 2, "manageAccountInfoBalance", accountInfoBalance);
        accountInfoTariffNameRow = addAccountInfoRow(grid, 3, "manageAccountInfoTariffName", accountInfoTariffName);
        accountInfoTariffPlanRow = addAccountInfoRow(grid, 4, "manageAccountInfoTariffPlan", accountInfoTariffPlan);
        accountInfoDefaultTimezoneRow = addAccountInfoRow(grid, 5, "manageAccountInfoDefaultTimezone", accountInfoDefaultTimezone);

        Region profileSpacer = new Region();
        profileSpacer.setMinHeight(2);
        HBox profileHeader = new HBox(8, accountInfoProfileToggle, accountInfoProfileTitleLabel, profileSpacer, accountInfoProfileCopyButton);
        HBox.setHgrow(profileSpacer, Priority.ALWAYS);
        accountInfoProfileBox.setCenter(accountInfoProfileLines);
        accountInfoProfileBox.setStyle("-fx-border-color: -fx-box-border; -fx-border-width: 1; -fx-border-radius: 4; -fx-padding: 6;");
        accountInfoProfileBox.setVisible(false);
        accountInfoProfileBox.setManaged(false);
        accountInfoProfileContainer.getChildren().setAll(profileHeader, accountInfoProfileBox);
        accountInfoProfileContainer.setVisible(false);
        accountInfoProfileContainer.setManaged(false);

        VBox content = new VBox(10, grid, accountInfoProfileContainer);
        content.setMaxWidth(Double.MAX_VALUE);
        configureAsCollapsibleGroup(
                I18n.tr("manageAccountInfoTitle"),
                I18n.tr("manageAccountInfoDescription"),
                content,
                true
        );
        setMaxWidth(Double.MAX_VALUE);
    }

    private void configureAsCollapsibleGroup(String title, String description, Node content, boolean collapsedByDefault) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("strong-label");
        VBox titleContainer = new VBox(4, titleLabel);
        titleContainer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleContainer, Priority.ALWAYS);

        final Label descriptionLabel;
        if (description != null && !description.isBlank()) {
            Label label = new Label(description);
            label.setWrapText(true);
            label.getStyleClass().add(STYLE_CLASS_DIM_LABEL);
            titleContainer.getChildren().add(label);
            descriptionLabel = label;
        } else {
            descriptionLabel = null;
        }

        Hyperlink toggleLink = new Hyperlink();
        toggleLink.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleContainer, spacer, toggleLink);

        Runnable refreshToggleLabel = () -> {
            boolean expanded = content.isVisible() && content.isManaged();
            toggleLink.setText(expanded ? I18n.tr("commonHide") : I18n.tr("commonShow"));
            if (descriptionLabel != null) {
                descriptionLabel.setVisible(expanded);
                descriptionLabel.setManaged(expanded);
            }
        };
        content.setVisible(!collapsedByDefault);
        content.setManaged(!collapsedByDefault);
        refreshToggleLabel.run();
        toggleLink.setOnAction(event -> {
            boolean expand = !(content.isVisible() && content.isManaged());
            content.setVisible(expand);
            content.setManaged(expand);
            refreshToggleLabel.run();
        });

        BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
        setTop(header);
        setCenter(content);
        setPadding(new Insets(10));
        getStyleClass().add("uiptv-card");
    }

    private void configureProfileJsonArea() {
        accountInfoProfileLines.setManaged(false);
        accountInfoProfileLines.setVisible(false);
        accountInfoProfileToggle.setMinSize(18, 18);
        accountInfoProfileToggle.setPrefSize(18, 18);
        accountInfoProfileToggle.setMaxSize(18, 18);
        accountInfoProfileToggle.setStyle("-fx-cursor: hand;");
        accountInfoProfileToggleIcon.setStyle("-fx-fill: -fx-text-base-color;");
        accountInfoProfileToggle.getChildren().setAll(accountInfoProfileToggleIcon);
        updateProfileToggleIcon(false);
        accountInfoProfileTitleLabel.setText(PROFILE_DATA_TITLE);
        accountInfoProfileToggle.setOnMouseClicked(event -> toggleProfileLines());
        accountInfoProfileCopyButton.setOnAction(event -> {
            String raw = accountInfoProfileRawJson != null ? accountInfoProfileRawJson : "";
            if (isBlank(raw)) {
                return;
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(raw);
            Clipboard.getSystemClipboard().setContent(content);
        });
    }

    private void toggleProfileLines() {
        boolean show = !(accountInfoProfileLines.isVisible() && accountInfoProfileLines.isManaged());
        accountInfoProfileLines.setVisible(show);
        accountInfoProfileLines.setManaged(show);
        accountInfoProfileBox.setVisible(show);
        accountInfoProfileBox.setManaged(show);
        updateProfileToggleIcon(show);
        accountInfoProfileTitleLabel.setText(show ? "Hide profile data" : PROFILE_DATA_TITLE);
    }

    private void updateProfileToggleIcon(boolean expanded) {
        if (expanded) {
            accountInfoProfileToggleIcon.setContent("M4 11 H20 V13 H4 Z");
        } else {
            accountInfoProfileToggleIcon.setContent("M4 11 H20 V13 H4 Z M11 4 H13 V20 H11 Z");
        }
    }

    private AccountInfoRow addAccountInfoRow(GridPane grid, int row, String labelKey, Node value) {
        Label label = new Label(I18n.tr(labelKey));
        grid.add(label, 0, row);
        grid.add(value, 1, row);
        return new AccountInfoRow(label, value);
    }

    private void setAccountInfoValue(AccountInfoRow row, Label label, String value) {
        String safeValue = safeText(value);
        boolean visible = isNotBlank(safeValue);
        label.setText(safeValue);
        setRowVisible(row, visible);
    }

    private void setRowVisible(AccountInfoRow row, boolean visible) {
        if (row == null) {
            return;
        }
        row.label.setVisible(visible);
        row.label.setManaged(visible);
        row.value.setVisible(visible);
        row.value.setManaged(visible);
    }

    private void setAccountInfoProfileJson(String value) {
        String safeValue = safeText(value);
        boolean visible = isNotBlank(safeValue);
        accountInfoProfileRawJson = safeValue;
        accountInfoProfileLines.getChildren().clear();
        if (visible) {
            String formatted = formatProfileJsonForDisplay(safeValue);
            if (isNotBlank(formatted)) {
                for (String line : formatted.split("\\R")) {
                    if (isBlank(line)) {
                        continue;
                    }
                    Label label = new Label(line);
                    label.setWrapText(true);
                    accountInfoProfileLines.getChildren().add(label);
                }
            }
        }
        accountInfoProfileContainer.setVisible(visible);
        accountInfoProfileContainer.setManaged(visible);
        if (visible) {
            accountInfoProfileLines.setVisible(false);
            accountInfoProfileLines.setManaged(false);
            accountInfoProfileBox.setVisible(false);
            accountInfoProfileBox.setManaged(false);
            updateProfileToggleIcon(false);
            accountInfoProfileTitleLabel.setText(PROFILE_DATA_TITLE);
        }
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

    private void updateStatusIndicator(String statusText) {
        AccountInfoUiUtil.StatusState state = AccountInfoUiUtil.resolveStatusState(statusText);
        String color = AccountInfoUiUtil.colorForStatus(state);
        AccountInfoUiUtil.applyIndicator(accountInfoStatusIndicator, color, state != AccountInfoUiUtil.StatusState.UNKNOWN);
    }

    private void updateExpiryIndicator(Instant instant, boolean hasValue) {
        if (!hasValue || instant == null) {
            AccountInfoUiUtil.applyIndicator(accountInfoExpiryIndicator, AccountInfoUiUtil.colorForExpiry(AccountInfoUiUtil.ExpiryState.UNKNOWN), false);
            return;
        }
        AccountInfoUiUtil.ExpiryState state = AccountInfoUiUtil.resolveExpiryState(instant);
        String color = AccountInfoUiUtil.colorForExpiry(state);
        AccountInfoUiUtil.applyIndicator(accountInfoExpiryIndicator, color, true);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String safeStatus(com.uiptv.model.AccountStatus status) {
        return status == null ? "" : status.toDisplay();
    }

    private static class AccountInfoRow {
        private final Label label;
        private final Node value;

        private AccountInfoRow(Label label, Node value) {
            this.label = label;
            this.value = value;
        }
    }
}
