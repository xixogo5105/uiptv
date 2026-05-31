package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.player.api.VideoPlayerInterface;
import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.remotesync.RemoteSyncClientService;
import com.uiptv.service.remotesync.RemoteSyncExecutionResult;
import com.uiptv.service.remotesync.RemoteSyncOptions;
import com.uiptv.service.remotesync.RemoteSyncProgressStep;
import com.uiptv.service.*;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.ui.util.UiServerUrlUtil;
import com.uiptv.util.I18n;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.UIptvAlert;
import com.uiptv.widget.UIptvText;
import com.uiptv.widget.UIptvTextArea;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ConfigurationUI extends VBox {
    private static final String EMBEDDED_PLAYER_PATH = PlaybackUIService.EMBEDDED_PLAYER_PATH;
    private static final String TMDB_API_GUIDE_URL = "https://developer.themoviedb.org/docs/getting-started";
    private static final String TMDB_API_KEY_URL = "https://www.themoviedb.org/settings/api";
    private static final String CONFIG_FILTER_CATEGORIES_PROMPT = "configFilterCategoriesPrompt";
    private static final String CONFIG_FILTER_CHANNELS_PROMPT = "configFilterChannelsPrompt";
    private static final String CONFIG_EMBED_PLAYER_RESTART_NEEDED = "configEmbedPlayerRestartNeeded";
    private static final String CONFIG_DATABASE_SYNC_IN_PROGRESS = "configDatabaseSyncInProgress";
    private static final String CONFIG_EXPORT_DATABASE = "configExportDatabase";
    private static final String CONFIG_IMPORT_DATABASE = "configImportDatabase";
    private static final String FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON = "filterLockUnlockManageFiltersReason";
    private static final String DIALOG_TITLE_COMMON_INFO = "commonInfo";
    private static final String STYLE_CLASS_DANGEROUS = "dangerous";
    private static final String STYLE_CLASS_DIM_LABEL = "dim-label";
    private static final String STYLE_CLASS_NO_DIM_DISABLED = "no-dim-disabled";
    private static final String STYLE_CLASS_HELP_LINK = "section-help-link";
    private static final String STYLE_CLASS_OUTLINE_PANE = "uiptv-outline-pane";
    private static final String STYLE_CLASS_CONFIGURATION_STATUS_ICON = "configuration-status-icon";
    private static final String STYLE_CLASS_CONFIGURATION_STATUS_ICON_ON = "configuration-status-icon-on";
    private static final String STYLE_CLASS_CONFIGURATION_STATUS_ICON_OFF = "configuration-status-icon-off";
    private static final String STYLE_CLASS_CONFIGURATION_STATUS_GLYPH = "configuration-status-icon-glyph";
    private static final String STATUS_ICON_CHECK_PATH = "M5.9 10.6 L3.1 7.8 L1.8 9.1 L5.9 13.2 L14.2 4.9 L12.9 3.6 Z";
    private static final String STATUS_ICON_CROSS_PATH = "M4 3 L3 4 L7 8 L3 12 L4 13 L8 9 L12 13 L13 12 L9 8 L13 4 L12 3 L8 7 Z";
    private static final double STATUS_ICON_SIZE = 18;
    private static final Duration STATUS_TITLE_REFRESH_INTERVAL = Duration.seconds(30);
    private static final double DATABASE_SYNC_POPUP_WIDTH = 672;
    private static final AtomicReference<Stage> activePublishM3u8PopupStage = new AtomicReference<>();
    private static final AtomicReference<Stage> activeDatabaseSyncPopupStage = new AtomicReference<>();
    final ToggleGroup group = new ToggleGroup();
    final Button browserButtonPlayerPath1 = new Button("...");
    final Button browserButtonPlayerPath2 = new Button("...");
    final Button browserButtonPlayerPath3 = new Button("...");
    final FileChooser fileChooser = new FileChooser();
    private final VBox contentContainer = new VBox();
    private final RadioButton defaultPlayer1 = new RadioButton("");
    private final RadioButton defaultPlayer2 = new RadioButton("");
    private final RadioButton defaultPlayer3 = new RadioButton("");
    private final RadioButton defaultEmbedPlayer = new RadioButton();
    private final RadioButton defaultWebBrowserPlayer = new RadioButton(I18n.tr("configDefaultWebBrowserPlayer"));
    private final UIptvText playerPath1 = new UIptvText("playerPath1", "configPlayerPath1Prompt", 5);
    private final UIptvText playerPath2 = new UIptvText("playerPath2", "configPlayerPath2Prompt", 5);
    private final UIptvText playerPath3 = new UIptvText("playerPath3", "configPlayerPath3Prompt", 5);
    private final UIptvTextArea filterCategoriesWithTextContains = new UIptvTextArea("filterCategoriesWithTextContains", CONFIG_FILTER_CATEGORIES_PROMPT, 5);
    private final UIptvTextArea filterChannelWithTextContains = new UIptvTextArea("filterChannelWithTextContains", CONFIG_FILTER_CHANNELS_PROMPT, 5);
    private final CheckBox filterPausedCheckBox = new CheckBox(I18n.tr("configPauseFiltering"));
    private final Label filterLockStatusLabel = new Label();
    private final Label filtersGroupTitleLabel = new Label();
    private final StatusIcon filtersGroupStatusIcon = new StatusIcon();
    private final Label cacheFilteringGroupTitleLabel = new Label();
    private final StatusIcon cacheFilteringGroupStatusIcon = new StatusIcon();
    private final Button filterLockPasswordButton = new Button();
    private final Button filterUnlockButton = new Button(I18n.tr("filterLockUnlockAction"));
    private final Button filterRelockButton = new Button(I18n.tr("filterLockLockNowAction"));
    private final CheckBox filterDisablePasswordCheckBox = new CheckBox(I18n.tr("filterLockDisablePasswordAction"));
    private final ComboBox<Integer> filterLockUnlockDurationComboBox = new ComboBox<>();
    private HBox filterLockDurationRow;
    private final VBox filterAdminControls = new VBox(10);
    private final CheckBox darkThemeCheckBox = new CheckBox(I18n.tr("configUseDarkTheme"));
    private final CheckBox autoRunServerOnStartupCheckBox = new CheckBox(I18n.tr("configAutoRunServerOnStartup"));
    private final CheckBox httpsServerEnabledCheckBox = new CheckBox(I18n.tr("configEnableHttpsServer"));
    private final CheckBox enableThumbnailsCheckBox = new CheckBox(I18n.tr("configEnableThumbnails"));
    private final CheckBox wideViewCheckBox = new CheckBox(I18n.tr("configWideView"));
    private final Hyperlink wideViewHelpLink = new Hyperlink("(?)");
    private final CheckBox resolveChainAndDeepRedirectsCheckBox = new CheckBox(I18n.tr("configResolveChainAndDeepRedirects"));
    private final Hyperlink resolveChainAndDeepRedirectsHelpLink = new Hyperlink("(?)");
    private final Hyperlink videoPlayersHelpLink = new Hyperlink("(?)");
    private final Hyperlink filtersHelpLink = new Hyperlink("(?)");
    private final Hyperlink themeHelpLink = new Hyperlink("(?)");
    private final Hyperlink cacheFilteringHelpLink = new Hyperlink("(?)");
    private final Hyperlink databaseSyncHelpLink = new Hyperlink("(?)");
    private final Hyperlink webServerHelpLink = new Hyperlink("(?)");
    private final Hyperlink tmdbMetadataHelpLink = new Hyperlink("(?)");
    private final Hyperlink vlcOptionsLink = new Hyperlink(I18n.tr("configVlcOptionsLink"));
    private final ComboBox<I18n.SupportedLanguage> languageComboBox = new ComboBox<>();
    private final ComboBox<Integer> themeZoomComboBox = new ComboBox<>();
    private final UIptvText serverPort = new UIptvText("serverPort", "configServerPortPrompt", 3);
    private final UIptvText httpsServerPort = new UIptvText("httpsServerPort", "configHttpsServerPortPrompt", 3);
    private final UIptvText cacheExpiryDays = new UIptvText("cacheExpiryDays", "configCacheExpiryPrompt", 5);
    private final PasswordField tmdbReadAccessToken = new PasswordField();
    private final Hyperlink tmdbApiGuideLink = new Hyperlink(I18n.tr("configTmdbApiGuideLink"));
    private final Hyperlink tmdbApiKeyPageLink = new Hyperlink(I18n.tr("configTmdbApiKeyPageLink"));
    private final Button startServerButton = new Button(I18n.tr("configStartServer"));
    private final Hyperlink openServerLink = new Hyperlink(I18n.tr("configOpenWebApp"));
    private final Hyperlink openSecureServerLink = new Hyperlink(I18n.tr("configOpenSecureWebApp"));
    private final Button publishM3u8Button = new Button(I18n.tr("configPublishM3u8"));
    private final Button clearCacheButton = new Button(I18n.tr("configClearCache"));
    private final Button clearWatchingNowButton = new Button(I18n.tr("configClearWatchingNow"));
    private final Button reloadCacheButton = new Button(I18n.tr("configReloadAccountsCache"));
    private final Button importDatabaseButton = new Button(I18n.tr(CONFIG_IMPORT_DATABASE));
    private final Button exportDatabaseButton = new Button(I18n.tr(CONFIG_EXPORT_DATABASE));
    private final ProminentButton saveButton = new ProminentButton(I18n.tr("commonSave"));
    private final FileChooser databaseFileChooser = new FileChooser();
    private final Callback<Object> onSaveCallback;
    private final ConfigurationService service = ConfigurationService.getInstance();
    private final ConfigurationApplicationService configurationApplicationService = ConfigurationApplicationService.getInstance();
    private final CacheService cacheService = new CacheServiceImpl();
    private final RemoteSyncClientService remoteSyncClientService = new RemoteSyncClientService();
    private final DatabaseBackupArchiveService databaseBackupArchiveService = DatabaseBackupArchiveService.getInstance();
    private String dbId;
    private String persistedFilterCategoriesValue = "";
    private String persistedFilterChannelsValue = "";
    private boolean persistedPauseFilteringValue = false;
    private boolean ignorePlayerSelectionPrompt = false;
    private String vlcNetworkCachingMs = ConfigurationService.DEFAULT_VLC_CACHING_MS;
    private String vlcLiveCachingMs = ConfigurationService.DEFAULT_VLC_CACHING_MS;
    private boolean vlcHttpUserAgentEnabled = true;
    private boolean vlcHttpForwardCookiesEnabled = true;
    @SuppressWarnings("java:S1450")
    private Timeline serverStatusTimeline;
    @SuppressWarnings("java:S1450")
    private Timeline statusTitleTimeline;
    @SuppressWarnings("java:S1450")
    private Timeline saveSuccessTimeline;
    private final ConfigurationChangeListener configurationChangeListener = revision -> javafx.application.Platform.runLater(this::refreshConfigurationForm);
    private final FilterLockService.LockStateChangeListener filterLockStateChangeListener = this::scheduleFilterLockUiRefresh;

    public ConfigurationUI(Callback<Object> onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
        initWidgets();
    }

    static void clearWatchingNowStates() {
        ConfigurationApplicationService.getInstance().clearWatchingNowState();
    }

    private void initWidgets() {
        setPadding(Insets.EMPTY);
        setSpacing(0);
        startServerButton.getStyleClass().add(STYLE_CLASS_NO_DIM_DISABLED);
        contentContainer.setPadding(new Insets(5));
        contentContainer.setSpacing(10);
        databaseFileChooser.setTitle(I18n.tr("configSelectDatabaseFile"));
        databaseFileChooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter(I18n.tr("configDatabaseFiles"), "*.zip"),
                new FileChooser.ExtensionFilter(I18n.tr("commonAll"), "*.*")
        );

        ScrollPane scrollPane = new ScrollPane(contentContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().setAll(scrollPane);

        Configuration configuration = service.read();
        initializeLanguageSelection(configuration);
        initializeThemeZoomSelection(configuration);
        configurePlayerToggleGroup();
        updateEmbeddedPlayerTitle();
        configurePlayerUserData();
        addWideViewHelpClickHandler();
        addVideoPlayersHelpClickHandler();
        addThemeHelpClickHandler();
        addDatabaseSyncHelpClickHandler();
        addFiltersHelpClickHandler();
        addCacheFilteringHelpClickHandler();
        addWebServerHelpClickHandler();
        addTmdbMetadataHelpClickHandler();
        configureHelpLinks();
        defaultEmbedPlayer.setSelected(true);
        applyConfigurationToForm(configuration);
        selectDefaultPlayer(configuration);
        updateVlcOptionsLinkVisibility();
        updateWideViewVisibility();
        if (cacheExpiryDays.getText() == null || cacheExpiryDays.getText().isBlank()) {
            cacheExpiryDays.setText(String.valueOf(ConfigurationService.DEFAULT_CACHE_EXPIRY_DAYS));
        }
        installDigitsOnlyFilter(serverPort);
        installDigitsOnlyFilter(httpsServerPort);
        cacheExpiryDays.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }
            String normalized = newVal.replaceAll("\\D", "");
            if (!newVal.equals(normalized)) {
                cacheExpiryDays.setText(normalized);
            }
        });
        playerPath1.setMinWidth(295);
        playerPath2.setMinWidth(295);
        playerPath3.setMinWidth(295);
        playerPath1.setPrefWidth(295);
        playerPath2.setPrefWidth(295);
        playerPath3.setPrefWidth(295);
        filterCategoriesWithTextContains.setMinWidth(250);
        filterChannelWithTextContains.setMinWidth(250);
        filterCategoriesWithTextContains.setPrefRowCount(6);
        filterChannelWithTextContains.setPrefRowCount(6);
        registerConfigurationChangeListener();

        filterPausedCheckBox.setMinWidth(250);
        filterLockStatusLabel.setWrapText(true);
        filterLockStatusLabel.getStyleClass().add(STYLE_CLASS_DIM_LABEL);
        cacheExpiryDays.setPrefColumnCount(4);
        cacheExpiryDays.setMaxWidth(70);
        Label cacheExpiryLabel = new Label(I18n.tr("configCacheExpiresInDays"));
        HBox cacheExpiryRow = new HBox(8, cacheExpiryLabel, cacheExpiryDays);
        saveButton.setMinWidth(40);
        saveButton.setPrefWidth(440);
        saveButton.setMinHeight(50);
        saveButton.setPrefHeight(50);
        fileChooser.setTitle(I18n.tr("configSelectStreamingPlayer"));
        tmdbReadAccessToken.setPromptText(I18n.tr("configTmdbReadAccessTokenPrompt"));
        tmdbReadAccessToken.setMinWidth(295);
        tmdbReadAccessToken.setPrefWidth(295);
        tmdbReadAccessToken.setMaxWidth(Double.MAX_VALUE);
        HBox box1 = new HBox(6, defaultPlayer1, playerPath1, browserButtonPlayerPath1);
        HBox box2 = new HBox(6, defaultPlayer2, playerPath2, browserButtonPlayerPath2);
        HBox box3 = new HBox(6, defaultPlayer3, playerPath3, browserButtonPlayerPath3);
        Region box4Spacer = new Region();
        HBox.setHgrow(box4Spacer, Priority.ALWAYS);
        HBox box4 = new HBox(6, defaultEmbedPlayer, box4Spacer, vlcOptionsLink);
        HBox box5 = new HBox(6, defaultWebBrowserPlayer);
        HBox wideViewRow = new HBox(4, wideViewCheckBox, wideViewHelpLink);
        HBox resolveChainRow = new HBox(4, resolveChainAndDeepRedirectsCheckBox, resolveChainAndDeepRedirectsHelpLink);
        box1.setAlignment(Pos.CENTER_LEFT);
        box2.setAlignment(Pos.CENTER_LEFT);
        box3.setAlignment(Pos.CENTER_LEFT);
        box4.setAlignment(Pos.CENTER_LEFT);
        box5.setAlignment(Pos.CENTER_LEFT);
        wideViewRow.setAlignment(Pos.CENTER_LEFT);
        resolveChainRow.setAlignment(Pos.CENTER_LEFT);
        wideViewCheckBox.setMaxWidth(Region.USE_PREF_SIZE);
        resolveChainAndDeepRedirectsCheckBox.setMaxWidth(Region.USE_PREF_SIZE);
        Label tmdbTokenLabel = new Label(I18n.tr("configTmdbReadAccessToken"));
        HBox tmdbLinksRow = new HBox(10, tmdbApiGuideLink, tmdbApiKeyPageLink);
        VBox tmdbConfigSection = new VBox(6, tmdbTokenLabel, tmdbReadAccessToken, tmdbLinksRow);
        tmdbConfigSection.getStyleClass().add(STYLE_CLASS_OUTLINE_PANE);
        VBox playersGroup = new VBox(10, box1, box2, box3, box4, box5, wideViewRow, resolveChainRow);
        resolveChainAndDeepRedirectsHelpLink.getStyleClass().add(STYLE_CLASS_NO_DIM_DISABLED);
        wideViewHelpLink.getStyleClass().add(STYLE_CLASS_NO_DIM_DISABLED);

        filterAdminControls.getChildren().setAll(filterCategoriesWithTextContains, filterChannelWithTextContains);
        HBox filterLockActions = new HBox(8, filterLockPasswordButton, filterUnlockButton, filterRelockButton);
        filterLockActions.setAlignment(Pos.CENTER_LEFT);
        filterLockDurationRow = createFilterLockDurationRow();
        VBox filtersGroup = new VBox(10, filterLockStatusLabel, filterLockActions, filterDisablePasswordCheckBox, filterLockDurationRow, filterAdminControls);

        VBox themeOverridesGroup = buildThemeOverrideGroup();

        HBox clearButtons = new HBox(10, clearCacheButton, clearWatchingNowButton);
        reloadCacheButton.setMaxWidth(Double.MAX_VALUE);
        VBox cacheGroup = new VBox(10, filterPausedCheckBox, cacheExpiryRow, clearButtons, reloadCacheButton);
        refreshConfigurationBlockTitles();

        openServerLink.setVisible(false);
        openServerLink.setManaged(false);
        openSecureServerLink.setVisible(false);
        openSecureServerLink.setManaged(false);
        HBox serverButtonWrapper = new HBox(10, serverPort, startServerButton, openServerLink);
        publishM3u8Button.setMaxWidth(Double.MAX_VALUE);
        publishM3u8Button.setPrefWidth(440);
        HBox autoRunServerOnStartupRow = new HBox(6, autoRunServerOnStartupCheckBox);
        autoRunServerOnStartupRow.setAlignment(Pos.CENTER_LEFT);
        autoRunServerOnStartupCheckBox.setMaxWidth(Region.USE_PREF_SIZE);
        HBox httpsServerRow = new HBox(10, httpsServerEnabledCheckBox, httpsServerPort, openSecureServerLink);
        httpsServerRow.setAlignment(Pos.CENTER_LEFT);
        httpsServerEnabledCheckBox.setMaxWidth(Region.USE_PREF_SIZE);
        httpsServerPort.disableProperty().bind(httpsServerEnabledCheckBox.selectedProperty().not());
        httpsServerEnabledCheckBox.selectedProperty().addListener((_, _, _) -> refreshServerStatusUI());
        VBox serverGroup = new VBox(10, serverButtonWrapper, httpsServerRow, publishM3u8Button, autoRunServerOnStartupRow);
        serverGroup.setFillWidth(true);
        VBox databaseSyncGroup = buildDatabaseSyncGroup();

        contentContainer.getChildren().addAll(
                createCollapsibleGroupPane(I18n.tr("configVideoPlayers"), playersGroup, false, videoPlayersHelpLink),
                createCollapsibleGroupPane(filtersGroupTitleLabel, filtersGroupStatusIcon, filtersGroup, true, filtersHelpLink),
                createCollapsibleGroupPane(I18n.tr("configDarkTheme"), themeOverridesGroup, true, themeHelpLink),
                createCollapsibleGroupPane(cacheFilteringGroupTitleLabel, cacheFilteringGroupStatusIcon, cacheGroup, true, cacheFilteringHelpLink),
                createCollapsibleGroupPane(I18n.tr("configDatabaseSyncTitle"), databaseSyncGroup, true, databaseSyncHelpLink),
                createCollapsibleGroupPane(I18n.tr("configWebServer"), serverGroup, true, webServerHelpLink),
                createCollapsibleGroupPane(I18n.tr("configTmdbMetadata"), tmdbConfigSection, true, tmdbMetadataHelpLink),
                saveButton
        );
        addSaveButtonClickHandler();
        addBrowserButton1ClickHandler();
        addBrowserButton2ClickHandler();
        addBrowserButton3ClickHandler();
        addStartServerButtonClickHandler();
        addClearCacheButtonClickHandler();
        addClearWatchingNowButtonClickHandler();
        addPublishM3u8ButtonClickHandler();
        addReloadCacheButtonClickHandler();
        addOpenServerLinkClickHandler();
        addTmdbGuideLinkClickHandler();
        addFilterLockButtonHandlers();
        addResolveChainHelpClickHandler();
        addDatabaseSyncButtonHandlers();
        addVlcOptionsLinkClickHandler();
        addThemePreviewHandlers();
        installPlayerSelectionConfirmationHandler();
        installServerStatusMonitor();
        installStatusTitleMonitor();
        refreshFilterLockUi();
    }

    private BorderPane createCollapsibleGroupPane(String title, Node content, boolean collapsedByDefault, Hyperlink helpLink) {
        return createCollapsibleGroupPane(new Label(title), content, collapsedByDefault, helpLink);
    }

    private BorderPane createCollapsibleGroupPane(Label titleLabel, Node content, boolean collapsedByDefault, Hyperlink helpLink) {
        return createCollapsibleGroupPane(titleLabel, null, content, collapsedByDefault, helpLink);
    }

    private BorderPane createCollapsibleGroupPane(Label titleLabel, Node statusIcon, Node content,
                                                 boolean collapsedByDefault, Hyperlink helpLink) {
        BorderPane pane = new BorderPane(content);
        titleLabel.getStyleClass().add("strong-label");
        HBox titleRow = new HBox(4, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (statusIcon != null) {
            titleRow.getChildren().add(statusIcon);
        }
        if (helpLink != null) {
            titleRow.getChildren().add(helpLink);
        }
        VBox titleContainer = new VBox(4, titleRow);
        titleContainer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleContainer, Priority.ALWAYS);

        Hyperlink toggleLink = new Hyperlink();
        toggleLink.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleContainer, spacer, toggleLink);

        final Runnable refreshToggleLabel = () -> {
            boolean expanded = content.isVisible() && content.isManaged();
            toggleLink.setText(expanded ? I18n.tr("commonHide") : I18n.tr("commonShow"));
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
        pane.setTop(header);
        pane.setPadding(new Insets(10));
        pane.getStyleClass().add("uiptv-card");
        return pane;
    }

    private void installDigitsOnlyFilter(TextInputControl control) {
        control.textProperty().addListener((_, _, newVal) -> {
            if (newVal == null) {
                return;
            }
            String normalized = newVal.replaceAll("\\D", "");
            if (!newVal.equals(normalized)) {
                control.setText(normalized);
            }
        });
    }

    private HBox createFilterLockDurationRow() {
        filterLockUnlockDurationComboBox.getStyleClass().add("uiptv-combo-box");
        filterLockUnlockDurationComboBox.setMaxWidth(Double.MAX_VALUE);
        filterLockUnlockDurationComboBox.getItems().setAll(15, 30, 45, 60, 120, 180);
        filterLockUnlockDurationComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item + " " + I18n.tr("commonMinutes"));
            }
        });
        filterLockUnlockDurationComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item + " " + I18n.tr("commonMinutes"));
            }
        });

        Label durationLabel = new Label(I18n.tr("filterLockUnlockDuration"));
        HBox row = new HBox(8, durationLabel, filterLockUnlockDurationComboBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setVisible(false);
        row.setManaged(false);

        return row;
    }

    private VBox buildThemeOverrideGroup() {
        languageComboBox.setMaxWidth(Double.MAX_VALUE);
        Label languageLabel = new Label(I18n.tr("configLanguage"));
        HBox.setHgrow(languageComboBox, Priority.ALWAYS);

        themeZoomComboBox.getStyleClass().add("uiptv-combo-box");
        themeZoomComboBox.setMaxWidth(Double.MAX_VALUE);
        Label themeZoomLabel = new Label(I18n.tr("configThemeZoom"));
        HBox.setHgrow(themeZoomComboBox, Priority.ALWAYS);

        GridPane languageAndZoomGrid = new GridPane();
        languageAndZoomGrid.setHgap(8);
        languageAndZoomGrid.setVgap(10);
        languageAndZoomGrid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints labelColumn = new ColumnConstraints();
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setHgrow(Priority.ALWAYS);
        controlColumn.setFillWidth(true);
        languageAndZoomGrid.getColumnConstraints().addAll(labelColumn, controlColumn);

        languageAndZoomGrid.add(languageLabel, 0, 0);
        languageAndZoomGrid.add(languageComboBox, 1, 0);
        languageAndZoomGrid.add(themeZoomLabel, 0, 1);
        languageAndZoomGrid.add(themeZoomComboBox, 1, 1);
        GridPane.setHgrow(languageComboBox, Priority.ALWAYS);
        GridPane.setHgrow(themeZoomComboBox, Priority.ALWAYS);

        VBox languageAndZoomSection = new VBox(10, languageAndZoomGrid);
        languageAndZoomSection.getStyleClass().add(STYLE_CLASS_OUTLINE_PANE);
        languageAndZoomSection.setMaxWidth(Double.MAX_VALUE);

        return new VBox(10, darkThemeCheckBox, enableThumbnailsCheckBox, languageAndZoomSection);
    }

    private void initializeLanguageSelection(Configuration configuration) {
        languageComboBox.getItems().setAll(I18n.getSupportedLanguages());
        languageComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(I18n.SupportedLanguage item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.nativeDisplayName());
            }
        });
        languageComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(I18n.SupportedLanguage item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.nativeDisplayName());
            }
        });

        I18n.SupportedLanguage selected = I18n.resolveSupportedLanguage(configuration == null ? null : configuration.getLanguageLocale());
        languageComboBox.getSelectionModel().select(selected);
    }

    private void initializeThemeZoomSelection(Configuration configuration) {
        themeZoomComboBox.getItems().setAll(ConfigurationService.FIREFOX_ZOOM_PERCENT_OPTIONS);
        themeZoomComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item + "%");
            }
        });
        themeZoomComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item + "%");
            }
        });
        int selected = service.normalizeUiZoomPercent(configuration == null ? null : configuration.getUiZoomPercent());
        themeZoomComboBox.getSelectionModel().select(Integer.valueOf(selected));
    }

    private String getSelectedLanguageTag() {
        I18n.SupportedLanguage selected = languageComboBox.getSelectionModel().getSelectedItem();
        return selected == null ? I18n.DEFAULT_LANGUAGE_TAG : selected.languageTag();
    }

    private void addThemePreviewHandlers() {
        themeZoomComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyThemePreview());
    }

    private void applyThemePreview() {
        Scene scene = getScene();
        RootApplication.applyTheme(
                scene,
                getClass(),
                darkThemeCheckBox.isSelected(),
                getSelectedThemeZoomPercent()
        );
    }

    private void addReloadCacheButtonClickHandler() {
        reloadCacheButton.setOnAction(event -> ReloadCachePopup.showPopup((Stage) getScene().getWindow(), null, this::notifyAccountsChanged));
    }

    private void notifyAccountsChanged() {
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
    }

    private void updateEmbeddedPlayerTitle() {
        VideoPlayerInterface.PlayerType playerType = MediaPlayerFactory.getPlayerType();
        String title = I18n.tr("configEmbeddedPlayer");
        if (playerType == VideoPlayerInterface.PlayerType.VLC) {
            title = I18n.tr("configEmbeddedPlayerVlc");
        }
        defaultEmbedPlayer.setText(title);
    }

    private void addClearCacheButtonClickHandler() {
        clearCacheButton.setOnAction(event -> {
            if (UIptvAlert.showConfirmationAlert(I18n.tr("configCacheClearConfirm"))) {
                try {
                    cacheService.clearAllCache();
                    showMessageAlert(I18n.tr("configCacheCleared"));
                } catch (Exception _) {
                    showMessageAlert(I18n.tr("configCacheClearFailed"));
                }
            }
        });
    }

    private void addClearWatchingNowButtonClickHandler() {
        clearWatchingNowButton.setOnAction(event -> {
            if (UIptvAlert.showConfirmationAlert(I18n.tr("configWatchNowConfirm"))) {
                try {
                    clearWatchingNowStates();
                    showMessageAlert(I18n.tr("configWatchNowCleared"));
                } catch (Exception _) {
                    showMessageAlert(I18n.tr("configWatchNowClearFailed"));
                }
            }
        });
    }

    private void addStartServerButtonClickHandler() {
        startServerButton.setOnAction(event -> {
            try {
                if (configurationApplicationService.isServerRunning()) {
                    configurationApplicationService.stopServer();
                } else {
                    configurationApplicationService.startServer();
                }
                refreshServerStatusUI();
                // showMessageAlert("Server started at " + ConfigurationService.getInstance().read().getServerPort()); // Removed alert
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to toggle local web server", e);
            }
        });
    }

    private void addPublishM3u8ButtonClickHandler() {
        publishM3u8Button.setOnAction(event -> {
            Stage activePopupStage = activePublishM3u8PopupStage.get();
            if (activePopupStage != null && activePopupStage.isShowing()) {
                activePopupStage.toFront();
                activePopupStage.requestFocus();
                return;
            }
            Stage popupStage = new Stage();
            M3U8PublicationPopup popup = new M3U8PublicationPopup(popupStage);
            Scene scene = new Scene(popup, 680, 560);
            UiI18n.applySceneOrientation(scene);
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
            popupStage.setTitle(I18n.tr("configPublishM3u8"));
            popupStage.setScene(scene);
            popupStage.setOnHidden(hiddenEvent -> activePublishM3u8PopupStage.compareAndSet(popupStage, null));
            activePublishM3u8PopupStage.set(popupStage);
            popupStage.show();
            popupStage.toFront();
        });
    }

    private void installServerStatusMonitor() {
        refreshServerStatusUI();
        serverStatusTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshServerStatusUI()));
        serverStatusTimeline.setCycleCount(Animation.INDEFINITE);
        serverStatusTimeline.play();

        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                if (serverStatusTimeline != null) {
                    serverStatusTimeline.stop();
                }
            } else if (serverStatusTimeline != null) {
                serverStatusTimeline.play();
                refreshServerStatusUI();
            }
        });
    }

    private void installStatusTitleMonitor() {
        refreshConfigurationBlockTitles();
        statusTitleTimeline = new Timeline(new KeyFrame(STATUS_TITLE_REFRESH_INTERVAL, event -> refreshConfigurationBlockTitles()));
        statusTitleTimeline.setCycleCount(Animation.INDEFINITE);
        statusTitleTimeline.play();

        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                if (statusTitleTimeline != null) {
                    statusTitleTimeline.stop();
                }
            } else if (statusTitleTimeline != null) {
                statusTitleTimeline.play();
                refreshConfigurationBlockTitles();
            }
        });
    }

    private void refreshConfigurationBlockTitles() {
        FilterLockService filterLockService = FilterLockService.getInstance();
        Configuration configuration = service.read();
        boolean parentalLockOn = filterLockService.hasPasswordConfigured() && !filterLockService.isUnlocked();
        boolean censoringOn = configuration == null || !configuration.isPauseFiltering();
        filtersGroupTitleLabel.setText(I18n.tr("configFilters"));
        cacheFilteringGroupTitleLabel.setText(I18n.tr("configCacheFiltering"));
        filtersGroupStatusIcon.setEnabled(parentalLockOn);
        cacheFilteringGroupStatusIcon.setEnabled(censoringOn);
    }

    private void refreshServerStatusUI() {
        boolean running = configurationApplicationService.isServerRunning();
        if (running) {
            if (!startServerButton.getStyleClass().contains(STYLE_CLASS_DANGEROUS)) {
                startServerButton.getStyleClass().add(STYLE_CLASS_DANGEROUS);
            }
        } else {
            startServerButton.getStyleClass().remove(STYLE_CLASS_DANGEROUS);
        }
        startServerButton.setText(running ? I18n.tr("configStopServer") : I18n.tr("configStartServer"));
        openServerLink.setVisible(running);
        openServerLink.setManaged(running);
        boolean secureLinkVisible = running && httpsServerEnabledCheckBox.isSelected();
        openSecureServerLink.setVisible(secureLinkVisible);
        openSecureServerLink.setManaged(secureLinkVisible);
    }

    private void configurePlayerToggleGroup() {
        defaultPlayer1.setToggleGroup(group);
        defaultPlayer2.setToggleGroup(group);
        defaultPlayer3.setToggleGroup(group);
        defaultEmbedPlayer.setToggleGroup(group);
        defaultWebBrowserPlayer.setToggleGroup(group);
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            updateVlcOptionsLinkVisibility();
            updateWideViewVisibility();
        });
    }

    private void configurePlayerUserData() {
        defaultPlayer1.setUserData("defaultPlayer1");
        defaultPlayer2.setUserData("defaultPlayer2");
        defaultPlayer3.setUserData("defaultPlayer3");
        defaultEmbedPlayer.setUserData("defaultEmbedPlayer");
        defaultWebBrowserPlayer.setUserData("defaultWebBrowserPlayer");
    }

    private void selectDefaultPlayer(Configuration configuration) {
        if (configuration == null) {
            return;
        }
        if (playerPath1.getText() != null && playerPath1.getText().equals(configuration.getDefaultPlayerPath())) {
            defaultPlayer1.setSelected(true);
        } else if (playerPath2.getText() != null && playerPath2.getText().equals(configuration.getDefaultPlayerPath())) {
            defaultPlayer2.setSelected(true);
        } else if (playerPath3.getText() != null && playerPath3.getText().equals(configuration.getDefaultPlayerPath())) {
            defaultPlayer3.setSelected(true);
        } else if (PlaybackUIService.WEB_BROWSER_PLAYER_PATH.equals(configuration.getDefaultPlayerPath())) {
            defaultWebBrowserPlayer.setSelected(true);
        } else {
            defaultEmbedPlayer.setSelected(true);
        }
    }

    private void addOpenServerLinkClickHandler() {
        openServerLink.setOnAction(event -> UiServerUrlUtil.openInBrowser(ServerUrlUtil.getLoopbackServerUrl() + "/"));
        openSecureServerLink.setOnAction(event -> UiServerUrlUtil.openInBrowser(ServerUrlUtil.getLoopbackSecureServerUrl() + "/"));
    }

    private void addTmdbGuideLinkClickHandler() {
        tmdbApiGuideLink.setOnAction(event -> UiServerUrlUtil.openInBrowser(TMDB_API_GUIDE_URL));
        tmdbApiKeyPageLink.setOnAction(event -> UiServerUrlUtil.openInBrowser(TMDB_API_KEY_URL));
    }

    private void addFilterLockButtonHandlers() {
        filterLockPasswordButton.setOnAction(event -> {
            FilterLockDialogs.openPasswordChangeDialog(this);
            refreshConfigurationForm();
        });
        filterUnlockButton.setOnAction(event -> {
            if (FilterLockDialogs.ensureUnlocked(this, FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON)) {
                refreshFilterLockUi();
            }
        });
        filterRelockButton.setOnAction(event -> {
            FilterLockService.getInstance().clearUnlockSession();
            refreshFilterLockUi();
        });
        filterDisablePasswordCheckBox.setOnAction(event -> {
            if (!filterDisablePasswordCheckBox.isSelected()) {
                return;
            }
            boolean disabled = FilterLockDialogs.openDisablePasswordDialog(this);
            filterDisablePasswordCheckBox.setSelected(false);
            if (disabled) {
                refreshConfigurationForm();
            }
        });
        filterPausedCheckBox.setOnAction(event -> {
            FilterLockService filterLockService = FilterLockService.getInstance();
            if (!filterLockService.hasPasswordConfigured() || filterLockService.isUnlocked()) {
                return;
            }
            if (!FilterLockDialogs.ensureUnlocked(this, FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON)) {
                filterPausedCheckBox.setSelected(persistedPauseFilteringValue);
                return;
            }
            refreshFilterLockUi();
            filterPausedCheckBox.setSelected(!persistedPauseFilteringValue);
        });
    }

    private void addVlcOptionsLinkClickHandler() {
        vlcOptionsLink.setOnAction(event -> openVlcOptionsPopup());
    }

    private void configureHelpLinks() {
        java.util.List.of(
                wideViewHelpLink,
                resolveChainAndDeepRedirectsHelpLink,
                videoPlayersHelpLink,
                filtersHelpLink,
                themeHelpLink,
                cacheFilteringHelpLink,
                databaseSyncHelpLink,
                webServerHelpLink,
                tmdbMetadataHelpLink
        ).forEach(this::configureHelpLink);
    }

    private void configureHelpLink(Hyperlink helpLink) {
        helpLink.setMinWidth(Region.USE_PREF_SIZE);
        helpLink.setPadding(Insets.EMPTY);
        helpLink.getStyleClass().add(STYLE_CLASS_HELP_LINK);
    }

    private void addResolveChainHelpClickHandler() {
        resolveChainAndDeepRedirectsHelpLink.setOnAction(event -> showResolveChainHelp());
    }

    private void addWideViewHelpClickHandler() {
        wideViewHelpLink.setOnAction(event -> showWideViewHelp());
    }

    private void addVideoPlayersHelpClickHandler() {
        videoPlayersHelpLink.setOnAction(event -> showVideoPlayersHelp());
    }

    private void addThemeHelpClickHandler() {
        themeHelpLink.setOnAction(event -> showThemeHelp());
    }

    private void addDatabaseSyncHelpClickHandler() {
        databaseSyncHelpLink.setOnAction(event -> showDatabaseSyncHelp());
    }

    private void addFiltersHelpClickHandler() {
        filtersHelpLink.setOnAction(event -> showFiltersHelp());
    }

    private void addCacheFilteringHelpClickHandler() {
        cacheFilteringHelpLink.setOnAction(event -> showCacheFilteringHelp());
    }

    private void addWebServerHelpClickHandler() {
        webServerHelpLink.setOnAction(event -> showWebServerHelp());
    }

    private void addTmdbMetadataHelpClickHandler() {
        tmdbMetadataHelpLink.setOnAction(event -> showTmdbMetadataHelp());
    }

    private void addDatabaseSyncButtonHandlers() {
        importDatabaseButton.setOnAction(event -> openDatabaseSyncPopup(true));
        exportDatabaseButton.setOnAction(event -> openDatabaseSyncPopup(false));
    }

    private void registerConfigurationChangeListener() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene == null && newScene != null) {
                service.addChangeListener(configurationChangeListener);
                FilterLockService.getInstance().addLockStateChangeListener(filterLockStateChangeListener);
            } else if (oldScene != null && newScene == null) {
                service.removeChangeListener(configurationChangeListener);
                FilterLockService.getInstance().removeLockStateChangeListener(filterLockStateChangeListener);
            }
        });
    }

    private void scheduleFilterLockUiRefresh() {
        if (getScene() == null) {
            return;
        }
        try {
            Platform.runLater(this::refreshFilterLockUi);
        } catch (IllegalStateException _) {
            // The JavaFX runtime may already be shutting down.
        }
    }

    private void refreshConfigurationForm() {
        if (getScene() == null) {
            return;
        }
        Configuration configuration = service.read();
        applyConfigurationToForm(configuration);
        selectDefaultPlayer(configuration);
        updateVlcOptionsLinkVisibility();
        updateWideViewVisibility();
        refreshServerStatusUI();
        refreshFilterLockUi();
        refreshConfigurationBlockTitles();
    }

    private void refreshFilterLockUi() {
        refreshConfigurationBlockTitles();
        FilterLockService filterLockService = FilterLockService.getInstance();
        boolean passwordSet = filterLockService.hasPasswordConfigured();
        boolean unlocked = filterLockService.isUnlocked();

        filterLockPasswordButton.setText(I18n.tr(passwordSet ? "filterLockChangePasswordAction" : "filterLockSetPasswordAction"));
        filterUnlockButton.setManaged(passwordSet && !unlocked);
        filterUnlockButton.setVisible(passwordSet && !unlocked);
        filterRelockButton.setManaged(passwordSet && unlocked);
        filterRelockButton.setVisible(passwordSet && unlocked);
        filterDisablePasswordCheckBox.setManaged(passwordSet);
        filterDisablePasswordCheckBox.setVisible(passwordSet);
        filterDisablePasswordCheckBox.setSelected(false);

        if (!passwordSet) {
            filterLockStatusLabel.setText(I18n.tr("filterLockStatusNotSet"));
            filterCategoriesWithTextContains.setEditable(true);
            filterChannelWithTextContains.setEditable(true);
            filterCategoriesWithTextContains.setPromptText(I18n.tr(CONFIG_FILTER_CATEGORIES_PROMPT));
            filterChannelWithTextContains.setPromptText(I18n.tr(CONFIG_FILTER_CHANNELS_PROMPT));
            filterCategoriesWithTextContains.setText(persistedFilterCategoriesValue);
            filterChannelWithTextContains.setText(persistedFilterChannelsValue);
            filterPausedCheckBox.setSelected(persistedPauseFilteringValue);
            updateFilterLockDurationRowVisibility(false);
            return;
        }

        if (unlocked) {
            filterLockStatusLabel.setText(I18n.tr("filterLockStatusUnlocked", filterLockService.getUnlockWindowMinutes()));
            filterCategoriesWithTextContains.setEditable(true);
            filterChannelWithTextContains.setEditable(true);
            filterCategoriesWithTextContains.setPromptText(I18n.tr(CONFIG_FILTER_CATEGORIES_PROMPT));
            filterChannelWithTextContains.setPromptText(I18n.tr(CONFIG_FILTER_CHANNELS_PROMPT));
            filterCategoriesWithTextContains.setText(persistedFilterCategoriesValue);
            filterChannelWithTextContains.setText(persistedFilterChannelsValue);
            filterPausedCheckBox.setSelected(persistedPauseFilteringValue);
            updateFilterLockDurationRowVisibility(true);
            return;
        }

        filterLockStatusLabel.setText(I18n.tr("filterLockStatusLocked", filterLockService.getUnlockWindowMinutes()));
        filterCategoriesWithTextContains.clear();
        filterChannelWithTextContains.clear();
        filterCategoriesWithTextContains.setEditable(false);
        filterChannelWithTextContains.setEditable(false);
        filterPausedCheckBox.setSelected(persistedPauseFilteringValue);
        filterCategoriesWithTextContains.setPromptText(I18n.tr("filterLockHiddenCategoriesPrompt"));
        filterChannelWithTextContains.setPromptText(I18n.tr("filterLockHiddenChannelsPrompt"));
        updateFilterLockDurationRowVisibility(false);
    }

    private void updateFilterLockDurationRowVisibility(boolean visible) {
        if (filterLockDurationRow != null) {
            filterLockDurationRow.setVisible(visible);
            filterLockDurationRow.setManaged(visible);
        }
    }

    private void applyConfigurationToForm(Configuration configuration) {
        if (configuration == null) {
            return;
        }
        this.dbId = configuration.getDbId();
        playerPath1.setText(configuration.getPlayerPath1());
        playerPath2.setText(configuration.getPlayerPath2());
        playerPath3.setText(configuration.getPlayerPath3());
        persistedFilterCategoriesValue = configuration.getFilterCategoriesList() == null ? "" : configuration.getFilterCategoriesList();
        persistedFilterChannelsValue = configuration.getFilterChannelsList() == null ? "" : configuration.getFilterChannelsList();
        persistedPauseFilteringValue = configuration.isPauseFiltering();
        filterCategoriesWithTextContains.setText(persistedFilterCategoriesValue);
        filterChannelWithTextContains.setText(persistedFilterChannelsValue);
        filterPausedCheckBox.setSelected(persistedPauseFilteringValue);
        darkThemeCheckBox.setSelected(configuration.isDarkTheme());
        enableThumbnailsCheckBox.setSelected(configuration.isEnableThumbnails());
        wideViewCheckBox.setSelected(configuration.isWideView());
        serverPort.setText(configuration.getServerPort());
        httpsServerEnabledCheckBox.setSelected(configuration.isHttpsServerEnabled());
        httpsServerPort.setText(defaultHttpsServerPort(configuration.getHttpsServerPort()));
        autoRunServerOnStartupCheckBox.setSelected(configuration.isAutoRunServerOnStartup());
        resolveChainAndDeepRedirectsCheckBox.setSelected(configuration.isResolveChainAndDeepRedirects());
        cacheExpiryDays.setText(String.valueOf(service.normalizeCacheExpiryDays(configuration.getCacheExpiryDays())));
        tmdbReadAccessToken.setText(configuration.getTmdbReadAccessToken());
        Integer duration = configuration.getFilterLockUnlockDurationMinutes() != null && !configuration.getFilterLockUnlockDurationMinutes().isEmpty()
            ? Integer.parseInt(configuration.getFilterLockUnlockDurationMinutes())
            : 15;
        filterLockUnlockDurationComboBox.setValue(duration);
        vlcNetworkCachingMs = service.normalizeVlcCachingMs(configuration.getVlcNetworkCachingMs());
        vlcLiveCachingMs = service.normalizeVlcCachingMs(configuration.getVlcLiveCachingMs());
        vlcHttpUserAgentEnabled = configuration.isEnableVlcHttpUserAgent();
        vlcHttpForwardCookiesEnabled = configuration.isEnableVlcHttpForwardCookies();
        languageComboBox.getSelectionModel().select(I18n.resolveSupportedLanguage(configuration.getLanguageLocale()));
        themeZoomComboBox.getSelectionModel().select(Integer.valueOf(service.normalizeUiZoomPercent(configuration.getUiZoomPercent())));
    }

    private String defaultHttpsServerPort(String configuredPort) {
        return configuredPort == null || configuredPort.isBlank()
                ? ServerUrlUtil.DEFAULT_HTTPS_SERVER_PORT
                : configuredPort.trim();
    }

    private void addSaveButtonClickHandler() {
        saveButton.setOnAction(_ -> {
            try {
                if (saveButton.isDisable()) {
                    return;
                }
                saveButton.setDisable(true);

                Configuration previous = service.read();
                boolean wasAlreadyUnlocked = wasFilterAlreadyUnlocked();
                if (!ensureFilterAccessForPendingSave()) {
                    saveButton.setDisable(false);
                    return;
                }
                Configuration newConfiguration = buildConfigurationToSave();
                saveConfiguration(newConfiguration);
                applyPostSaveEffects(previous, newConfiguration);

                  // Restore original lock state if user was not already unlocked
                if (!wasAlreadyUnlocked) {
                    FilterLockService.getInstance().clearUnlockSession();
                }

                showSaveSuccessAnimation();
                if (restartRequired(previous, newConfiguration)) {
                    showMessageAlert(I18n.tr(CONFIG_EMBED_PLAYER_RESTART_NEEDED));
                }
            } catch (Exception _) {
                showErrorAlert(I18n.tr("configFailedToSave"));
                saveButton.setDisable(false);
            }
        });
    }

    private Configuration buildConfigurationToSave() {
        Configuration configuration = new Configuration(
                playerPath1.getText(), playerPath2.getText(), playerPath3.getText(), resolveDefaultPlayerPath(),
                resolveFilterCategoriesValueForSave(), resolveFilterChannelsValueForSave(),
                resolvePauseFilteringValueForSave(),
                darkThemeCheckBox.isSelected(), serverPort.getText(),
                defaultEmbedPlayer.isSelected(),
                sanitizeCacheExpiryDaysText(),
                enableThumbnailsCheckBox.isSelected()
        );
        configuration.setDbId(dbId);
        configuration.setWideView(wideViewCheckBox.isSelected());
        configuration.setLanguageLocale(getSelectedLanguageTag());
        configuration.setTmdbReadAccessToken(tmdbReadAccessToken.getText() == null ? "" : tmdbReadAccessToken.getText().trim());
        configuration.setFilterLockHash(service.read().getFilterLockHash());
        Integer saveDuration = filterLockUnlockDurationComboBox.getValue();
        configuration.setFilterLockUnlockDurationMinutes(saveDuration != null ? String.valueOf(saveDuration) : "15");
        configuration.setUiZoomPercent(String.valueOf(getSelectedThemeZoomPercent()));
        configuration.setAutoRunServerOnStartup(autoRunServerOnStartupCheckBox.isSelected());
        configuration.setHttpsServerEnabled(httpsServerEnabledCheckBox.isSelected());
        configuration.setHttpsServerPort(httpsServerPort.getText());
        configuration.setResolveChainAndDeepRedirects(resolveChainAndDeepRedirectsCheckBox.isSelected());
        configuration.setVlcNetworkCachingMs(vlcNetworkCachingMs);
        configuration.setVlcLiveCachingMs(vlcLiveCachingMs);
        configuration.setEnableVlcHttpUserAgent(vlcHttpUserAgentEnabled);
        configuration.setEnableVlcHttpForwardCookies(vlcHttpForwardCookiesEnabled);
        return configuration;
    }

    private boolean wasFilterAlreadyUnlocked() {
        FilterLockService filterLockService = FilterLockService.getInstance();
        if (!filterLockService.hasPasswordConfigured()) {
            return true;
         }
        return filterLockService.isUnlocked();
      }

    private boolean ensureFilterAccessForPendingSave() {
        FilterLockService filterLockService = FilterLockService.getInstance();
        if (!filterLockService.hasPasswordConfigured() || filterLockService.isUnlocked()) {
            return true;
        }
        boolean filterValuesChanged = !java.util.Objects.equals(filterCategoriesWithTextContains.getText(), persistedFilterCategoriesValue)
                || !java.util.Objects.equals(filterChannelWithTextContains.getText(), persistedFilterChannelsValue)
                || filterPausedCheckBox.isSelected() != persistedPauseFilteringValue;
        if (!filterValuesChanged) {
            return true;
        }
        boolean unlocked = FilterLockDialogs.ensureUnlocked(this, FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON);
        if (unlocked) {
            refreshFilterLockUi();
        }
        return unlocked;
    }

    private String resolveFilterCategoriesValueForSave() {
        if (FilterLockService.getInstance().hasPasswordConfigured() && !FilterLockService.getInstance().isUnlocked()) {
            return persistedFilterCategoriesValue;
        }
        return filterCategoriesWithTextContains.getText();
    }

    private String resolveFilterChannelsValueForSave() {
        if (FilterLockService.getInstance().hasPasswordConfigured() && !FilterLockService.getInstance().isUnlocked()) {
            return persistedFilterChannelsValue;
        }
        return filterChannelWithTextContains.getText();
    }

    private boolean resolvePauseFilteringValueForSave() {
        if (FilterLockService.getInstance().hasPasswordConfigured() && !FilterLockService.getInstance().isUnlocked()) {
            return persistedPauseFilteringValue;
        }
        return filterPausedCheckBox.isSelected();
    }

    private String resolveDefaultPlayerPath() {
        if (defaultPlayer1.isSelected()) return playerPath1.getText();
        if (defaultPlayer2.isSelected()) return playerPath2.getText();
        if (defaultPlayer3.isSelected()) return playerPath3.getText();
        if (defaultWebBrowserPlayer.isSelected()) return PlaybackUIService.WEB_BROWSER_PLAYER_PATH;
        return EMBEDDED_PLAYER_PATH;
    }

    private void saveConfiguration(Configuration configuration) {
        service.save(configuration);
        I18n.setLocale(configuration.getLanguageLocale());
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
    }

    private void applyPostSaveEffects(Configuration previous, Configuration newConfiguration) {
        if (thumbnailModeChanged(previous, newConfiguration)) {
            ThumbnailAwareUI.notifyThumbnailModeChanged(newConfiguration.isEnableThumbnails());
        }
        if (vlcSettingsChanged(previous, newConfiguration)) {
            MediaPlayerFactory.release();
        }
    }

    private boolean thumbnailModeChanged(Configuration previous, Configuration current) {
        boolean previousThumbnailsEnabled = previous != null && previous.isEnableThumbnails();
        return previousThumbnailsEnabled != current.isEnableThumbnails();
    }

    private boolean restartRequired(Configuration previous, Configuration current) {
        boolean previousEmbeddedPlayer = previous != null && previous.isEmbeddedPlayer();
        boolean previousWideView = previous != null && previous.isWideView();
        return previousEmbeddedPlayer != current.isEmbeddedPlayer()
                || previousWideView != current.isWideView()
                || !Objects.equals(previous == null ? null : previous.getLanguageLocale(), current.getLanguageLocale());
    }

    private boolean vlcSettingsChanged(Configuration previous, Configuration current) {
        if (previous == null) {
            return current != null;
        }
        return !Objects.equals(service.normalizeVlcCachingMs(previous.getVlcNetworkCachingMs()), service.normalizeVlcCachingMs(current.getVlcNetworkCachingMs()))
                || !Objects.equals(service.normalizeVlcCachingMs(previous.getVlcLiveCachingMs()), service.normalizeVlcCachingMs(current.getVlcLiveCachingMs()))
                || previous.isEnableVlcHttpUserAgent() != current.isEnableVlcHttpUserAgent()
                || previous.isEnableVlcHttpForwardCookies() != current.isEnableVlcHttpForwardCookies();
    }

    private void updateVlcOptionsLinkVisibility() {
        boolean visible = defaultEmbedPlayer.isSelected()
                && MediaPlayerFactory.getPlayerType() == VideoPlayerInterface.PlayerType.VLC;
        vlcOptionsLink.setVisible(visible);
        vlcOptionsLink.setManaged(visible);
    }

    private void updateWideViewVisibility() {
        boolean isEmbedded = defaultEmbedPlayer.isSelected();
        wideViewCheckBox.setVisible(isEmbedded);
        wideViewCheckBox.setManaged(isEmbedded);
        if (!isEmbedded) {
            wideViewCheckBox.setSelected(false);
        }
    }

    private void openVlcOptionsPopup() {
        Stage popupStage = new Stage();
        popupStage.initOwner(getScene() == null ? RootApplication.getPrimaryStage() : (Stage) getScene().getWindow());
        popupStage.setTitle(I18n.tr("configVlcPopupTitle"));

        ComboBox<VlcCachingOption> networkCachingComboBox = createVlcCachingComboBox();
        ComboBox<VlcCachingOption> liveCachingComboBox = createVlcCachingComboBox();
        CheckBox userAgentCheckBox = new CheckBox(I18n.tr("configVlcEnableUserAgent"));
        CheckBox forwardCookiesCheckBox = new CheckBox(I18n.tr("configVlcForwardCookies"));

        Runnable loadCurrentValues = () -> {
            networkCachingComboBox.getSelectionModel().select(VlcCachingOption.fromValue(vlcNetworkCachingMs));
            liveCachingComboBox.getSelectionModel().select(VlcCachingOption.fromValue(vlcLiveCachingMs));
            userAgentCheckBox.setSelected(vlcHttpUserAgentEnabled);
            forwardCookiesCheckBox.setSelected(vlcHttpForwardCookiesEnabled);
        };
        loadCurrentValues.run();

        Button saveVlcOptionsButton = new Button(I18n.tr("commonSave"));
        Button resetVlcOptionsButton = new Button(I18n.tr("configVlcResetDefaults"));
        Button closeButton = new Button(I18n.tr("commonClose"));

        saveVlcOptionsButton.setOnAction(event -> {
            vlcNetworkCachingMs = selectedCachingValue(networkCachingComboBox);
            vlcLiveCachingMs = selectedCachingValue(liveCachingComboBox);
            vlcHttpUserAgentEnabled = userAgentCheckBox.isSelected();
            vlcHttpForwardCookiesEnabled = forwardCookiesCheckBox.isSelected();
            saveVlcOptionsConfiguration(true);
            popupStage.close();
        });
        resetVlcOptionsButton.setOnAction(event -> {
            vlcNetworkCachingMs = ConfigurationService.DEFAULT_VLC_CACHING_MS;
            vlcLiveCachingMs = ConfigurationService.DEFAULT_VLC_CACHING_MS;
            vlcHttpUserAgentEnabled = true;
            vlcHttpForwardCookiesEnabled = true;
            saveVlcOptionsConfiguration(true);
            popupStage.close();
        });
        closeButton.setOnAction(event -> popupStage.close());

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.add(new Label(I18n.tr("configVlcNetworkCaching")), 0, 0);
        gridPane.add(networkCachingComboBox, 1, 0);
        gridPane.add(new Label(I18n.tr("configVlcLiveCaching")), 0, 1);
        gridPane.add(liveCachingComboBox, 1, 1);
        gridPane.add(userAgentCheckBox, 1, 2);
        gridPane.add(forwardCookiesCheckBox, 1, 3);
        GridPane.setHgrow(networkCachingComboBox, Priority.ALWAYS);
        GridPane.setHgrow(liveCachingComboBox, Priority.ALWAYS);

        HBox buttons = new HBox(10, saveVlcOptionsButton, resetVlcOptionsButton, closeButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12,
                new Label(I18n.tr("configVlcPopupDescription")),
                gridPane,
                buttons
        );
        root.setPadding(new Insets(14));

        Scene scene = new Scene(root, 520, Region.USE_COMPUTED_SIZE);
        UiI18n.applySceneOrientation(scene);
        scene.getStylesheets().add(RootApplication.getCurrentTheme());
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private ComboBox<VlcCachingOption> createVlcCachingComboBox() {
        ComboBox<VlcCachingOption> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(VlcCachingOption.all());
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(VlcCachingOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(VlcCachingOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        return comboBox;
    }

    private String selectedCachingValue(ComboBox<VlcCachingOption> comboBox) {
        VlcCachingOption option = comboBox.getSelectionModel().getSelectedItem();
        return option == null ? ConfigurationService.DEFAULT_VLC_CACHING_MS : option.value();
    }

    private VBox buildDatabaseSyncGroup() {
        importDatabaseButton.setMaxWidth(Double.MAX_VALUE);
        exportDatabaseButton.setMaxWidth(Double.MAX_VALUE);
        return new VBox(10, importDatabaseButton, exportDatabaseButton);
    }

    private void saveVlcOptionsConfiguration(boolean showReloadMessage) {
        Configuration previous = service.read();
        Configuration configuration = service.read();
        configuration.setVlcNetworkCachingMs(vlcNetworkCachingMs);
        configuration.setVlcLiveCachingMs(vlcLiveCachingMs);
        configuration.setEnableVlcHttpUserAgent(vlcHttpUserAgentEnabled);
        configuration.setEnableVlcHttpForwardCookies(vlcHttpForwardCookiesEnabled);
        service.save(configuration);
        applyPostSaveEffects(previous, configuration);
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
        if (showReloadMessage && vlcSettingsChanged(previous, configuration)) {
            showMessageAlert(I18n.tr(CONFIG_EMBED_PLAYER_RESTART_NEEDED));
        }
    }

    private void openDatabaseSyncPopup(boolean importMode) {
        Stage activePopupStage = activeDatabaseSyncPopupStage.get();
        if (activePopupStage != null && activePopupStage.isShowing()) {
            activePopupStage.close();
        }
        Stage popupStage = new Stage();
        popupStage.initOwner(getScene() == null ? RootApplication.getPrimaryStage() : (Stage) getScene().getWindow());

        ToggleGroup locationModeGroup = new ToggleGroup();
        RadioButton fileModeButton = new RadioButton(I18n.tr("configDatabaseSyncModeFile"));
        RadioButton remoteModeButton = new RadioButton(I18n.tr("configDatabaseSyncModeRemote"));
        fileModeButton.setToggleGroup(locationModeGroup);
        remoteModeButton.setToggleGroup(locationModeGroup);
        fileModeButton.setSelected(true);
        TextField databasePathField = new TextField();
        databasePathField.setPromptText(DatabaseBackupArchiveService.defaultFileName(System.currentTimeMillis() / 1000L));
        databasePathField.setPrefWidth(380);
        TextField remoteHostField = new TextField();
        remoteHostField.setPromptText(I18n.tr("configDatabaseSyncRemoteHostPrompt"));
        remoteHostField.setPrefWidth(220);
        TextField remotePortField = new TextField();
        remotePortField.setPromptText(I18n.tr("configDatabaseSyncRemotePortPrompt"));
        remotePortField.setPrefWidth(90);
        Button testConnectionButton = new Button(I18n.tr("configDatabaseSyncTestConnection"));
        Button browseButton = new Button("...");
        CheckBox syncConfigurationCheckBox = new CheckBox(I18n.tr("configSyncConfiguration"));
        CheckBox syncExternalPlayerPathsCheckBox = new CheckBox(I18n.tr("configSyncExternalPlayerPaths"));
        syncExternalPlayerPathsCheckBox.disableProperty().bind(
                syncConfigurationCheckBox.selectedProperty().not()
                        .or(syncConfigurationCheckBox.disabledProperty())
        );
        Button runButton = new Button(I18n.tr(databaseSyncActionKey(importMode)));
        Button cancelButton = new Button(I18n.tr("commonClose"));
        ProgressBar progressBar = new ProgressBar(0);
        Label progressLabel = new Label();
        TextArea resultTextArea = new TextArea();
        AtomicBoolean syncRunning = new AtomicBoolean(false);

        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        progressLabel.setWrapText(true);
        progressLabel.getStyleClass().add(STYLE_CLASS_DIM_LABEL);
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);
        resultTextArea.setEditable(false);
        resultTextArea.setWrapText(true);
        resultTextArea.setPrefRowCount(12);
        resultTextArea.setVisible(false);
        resultTextArea.setManaged(false);

        bindManagedVisibility(databasePathField, fileModeButton.selectedProperty());
        bindManagedVisibility(browseButton, fileModeButton.selectedProperty());
        bindManagedVisibility(remoteHostField, remoteModeButton.selectedProperty());
        bindManagedVisibility(remotePortField, remoteModeButton.selectedProperty());
        bindManagedVisibility(testConnectionButton, remoteModeButton.selectedProperty());
        bindManagedVisibility(syncConfigurationCheckBox, remoteModeButton.selectedProperty());
        bindManagedVisibility(syncExternalPlayerPathsCheckBox, remoteModeButton.selectedProperty());

        browseButton.setOnAction(event -> {
            configureDatabaseBackupFileChooser(importMode);
            File selected = importMode
                    ? databaseFileChooser.showOpenDialog(popupStage)
                    : databaseFileChooser.showSaveDialog(popupStage);
            if (selected != null) {
                databasePathField.setText((importMode ? selected : ensureZipExtension(selected)).getAbsolutePath());
            }
        });
        testConnectionButton.setOnAction(event -> testRemoteDatabaseConnection(remoteHostField.getText(), remotePortField.getText(), testConnectionButton));

        DatabaseSyncDialogControls controls = new DatabaseSyncDialogControls(
                databasePathField,
                remoteHostField,
                remotePortField,
                browseButton,
                testConnectionButton,
                syncConfigurationCheckBox,
                syncExternalPlayerPathsCheckBox,
                runButton,
                cancelButton,
                progressBar,
                progressLabel,
                resultTextArea,
                syncRunning
        );

        runButton.setOnAction(event -> runDatabaseSyncAction(new DatabaseSyncRunRequest(
                popupStage,
                importMode,
                fileModeButton.isSelected(),
                databasePathField.getText(),
                remoteHostField.getText(),
                remotePortField.getText(),
                syncConfigurationCheckBox.isSelected(),
                syncExternalPlayerPathsCheckBox.isSelected(),
                controls
        )));
        cancelButton.setOnAction(event -> popupStage.close());
        popupStage.setOnCloseRequest(event -> {
            if (syncRunning.get()) {
                event.consume();
            }
        });

        HBox modeRow = new HBox(12, fileModeButton, remoteModeButton);
        HBox pathRow = new HBox(8, databasePathField, browseButton);
        HBox.setHgrow(databasePathField, Priority.ALWAYS);
        HBox remoteRow = new HBox(
                8,
                new Label(I18n.tr("configDatabaseSyncRemoteHost")),
                remoteHostField,
                new Label(I18n.tr("configDatabaseSyncRemotePort")),
                remotePortField,
                testConnectionButton
        );
        HBox.setHgrow(remoteHostField, Priority.ALWAYS);
        bindManagedVisibility(pathRow, fileModeButton.selectedProperty());
        bindManagedVisibility(remoteRow, remoteModeButton.selectedProperty());
        HBox buttons = new HBox(10, runButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Label descriptionLabel = new Label();
        descriptionLabel.setWrapText(true);

        Runnable textUpdater = () -> updateDatabaseSyncDirectionalText(
                popupStage,
                descriptionLabel,
                runButton,
                importMode,
                fileModeButton.isSelected()
        );
        fileModeButton.selectedProperty().addListener((obs, oldValue, newValue) -> {
            textUpdater.run();
            resizeDatabaseSyncPopup(popupStage);
        });
        remoteModeButton.selectedProperty().addListener((obs, oldValue, newValue) -> {
            textUpdater.run();
            resizeDatabaseSyncPopup(popupStage);
        });
        textUpdater.run();

        VBox root = new VBox(
                12,
                descriptionLabel,
                modeRow,
                pathRow,
                remoteRow,
                syncConfigurationCheckBox,
                syncExternalPlayerPathsCheckBox,
                progressBar,
                progressLabel,
                resultTextArea,
                buttons
        );
        root.setPadding(new Insets(14));

        Scene scene = new Scene(root, DATABASE_SYNC_POPUP_WIDTH, Region.USE_COMPUTED_SIZE);
        UiI18n.applySceneOrientation(scene);
        scene.getStylesheets().add(RootApplication.getCurrentTheme());
        popupStage.setScene(scene);
        popupStage.setOnShown(event -> resizeDatabaseSyncPopup(popupStage));
        popupStage.setOnHidden(hiddenEvent -> activeDatabaseSyncPopupStage.compareAndSet(popupStage, null));
        activeDatabaseSyncPopupStage.set(popupStage);
        popupStage.showAndWait();
    }

    private void resizeDatabaseSyncPopup(Stage popupStage) {
        if (popupStage.getScene() == null) {
            return;
        }
        popupStage.sizeToScene();
        Platform.runLater(() -> {
            if (popupStage.isShowing()) {
                popupStage.sizeToScene();
            }
        });
    }

    private void updateDatabaseSyncDirectionalText(Stage popupStage,
                                                   Label descriptionLabel,
                                                   Button runButton,
                                                   boolean importMode,
                                                   boolean fileMode) {
        String sourceLabel = databaseSyncSourceLabel(importMode, fileMode);
        String destinationLabel = databaseSyncDestinationLabel(importMode, fileMode);
        popupStage.setTitle(I18n.tr("configDatabaseSyncDirectionTitle", sourceLabel, destinationLabel));
        descriptionLabel.setText(I18n.tr("configDatabaseSyncDirectionDescription", sourceLabel, destinationLabel));
        runButton.setText(I18n.tr(databaseSyncActionKey(importMode)));
    }

    private String databaseSyncSourceLabel(boolean importMode, boolean fileMode) {
        if (importMode) {
            return I18n.tr(fileMode ? "configDatabaseSyncSelectedFile" : "configDatabaseSyncRemoteMachine");
        }
        return I18n.tr("configDatabaseSyncThisMachine");
    }

    private String databaseSyncDestinationLabel(boolean importMode, boolean fileMode) {
        if (importMode) {
            return I18n.tr("configDatabaseSyncThisMachine");
        }
        return I18n.tr(fileMode ? "configDatabaseSyncSelectedFile" : "configDatabaseSyncRemoteMachine");
    }

    private void runDatabaseSyncAction(DatabaseSyncRunRequest request) {
        if (!request.fileMode()) {
            runRemoteDatabaseSyncAction(
                    request.popupStage(),
                    request.importMode(),
                    request.remoteHost(),
                    request.remotePort(),
                    request.syncConfiguration(),
                    request.syncExternalPlayerPaths(),
                    request.controls()
            );
            return;
        }
        runLocalDatabaseBackupAction(request);
    }

    private void runLocalDatabaseBackupAction(DatabaseSyncRunRequest request) {
        String normalizedPath = normalizeSelectedPath(request.selectedPath());
        if (isMissingDatabasePath(normalizedPath)) {
            showErrorAlert(I18n.tr("configDatabaseSyncPathRequired"));
            return;
        }
        String selectedBackupPath = request.importMode() ? normalizedPath : ensureZipExtension(normalizedPath);
        if (isMissingImportSource(request.importMode(), selectedBackupPath)) {
            showErrorAlert(I18n.tr("configDatabaseSyncPathMissing"));
            return;
        }
        if (request.importMode() && !UIptvAlert.showConfirmationAlert(I18n.tr("configRestoreBackupConfirm"))) {
            return;
        }

        Configuration previousConfiguration = request.importMode() ? service.read() : null;

        DatabaseSyncDialogControls controls = request.controls();
        setDatabaseSyncControlsDisabled(true, controls);
        controls.syncRunning().set(true);
        controls.cancelButton().setDisable(true);
        controls.runButton().setVisible(false);
        controls.runButton().setManaged(false);
        controls.progressBar().setVisible(true);
        controls.progressBar().setManaged(true);
        controls.progressLabel().setVisible(true);
        controls.progressLabel().setManaged(true);
        controls.resultTextArea().clear();
        controls.resultTextArea().setVisible(false);
        controls.resultTextArea().setManaged(false);
        controls.progressBar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        controls.progressLabel().setText(I18n.tr(CONFIG_DATABASE_SYNC_IN_PROGRESS));
        request.popupStage().sizeToScene();

        Task<DatabaseBackupArchiveService.BackupArchiveReport> task = new Task<>() {
            @Override
            protected DatabaseBackupArchiveService.BackupArchiveReport call() throws Exception {
                String liveDatabasePath = configurationApplicationService.getDatabasePath();
                if (request.importMode()) {
                    return databaseBackupArchiveService.restoreBackupArchive(selectedBackupPath, liveDatabasePath);
                }
                return databaseBackupArchiveService.createBackupArchive(liveDatabasePath, selectedBackupPath);
            }
        };

        task.setOnSucceeded(event -> {
            controls.syncRunning().set(false);
            applyPostDatabaseImport(request.importMode(), previousConfiguration, true);
            refreshAppDataAfterDatabaseChange(request.importMode());
            controls.progressBar().setProgress(1);
            controls.progressLabel().setText(I18n.tr(request.importMode() ? "configImportDatabaseSuccess" : "configExportDatabaseSuccess"));
            controls.resultTextArea().setText(buildDatabaseBackupSummary(request.importMode(), task.getValue()));
            controls.resultTextArea().setVisible(true);
            controls.resultTextArea().setManaged(true);
            controls.cancelButton().setDisable(false);
            request.popupStage().sizeToScene();
        });

        task.setOnFailed(event -> {
            controls.syncRunning().set(false);
            controls.progressBar().setProgress(1);
            controls.progressLabel().setText(I18n.tr(request.importMode() ? "configImportDatabaseFailed" : "configExportDatabaseFailed"));
            controls.resultTextArea().setText(I18n.tr("configDatabaseSyncFailedWithReason",
                    I18n.tr(databaseSyncActionKey(request.importMode())),
                    summarizeExceptionMessage(task.getException())));
            controls.resultTextArea().setVisible(true);
            controls.resultTextArea().setManaged(true);
            controls.cancelButton().setDisable(false);
            request.popupStage().sizeToScene();
        });

        Thread worker = new Thread(task, request.importMode() ? "database-restore-task" : "database-backup-task");
        worker.setDaemon(true);
        worker.start();
    }

    private void runRemoteDatabaseSyncAction(Stage popupStage,
                                             boolean importMode,
                                             String remoteHost,
                                             String remotePort,
                                             boolean syncConfiguration,
                                             boolean syncExternalPlayerPaths,
                                             DatabaseSyncDialogControls controls) {
        String normalizedHost = normalizeSelectedPath(remoteHost);
        String normalizedPort = normalizeSelectedPath(remotePort);
        if (normalizedHost.isBlank()) {
            showErrorAlert(I18n.tr("configDatabaseSyncRemoteHostRequired"));
            return;
        }
        if (normalizedPort.isBlank()) {
            showErrorAlert(I18n.tr("configDatabaseSyncRemotePortRequired"));
            return;
        }
        int port = parseRemotePort(normalizedPort);
        if (port <= 0) {
            showErrorAlert(I18n.tr("configDatabaseSyncRemotePortInvalid"));
            return;
        }

        Configuration previousConfiguration = importMode ? service.read() : null;
        setDatabaseSyncControlsDisabled(true, controls);
        controls.syncRunning().set(true);
        controls.cancelButton().setDisable(true);
        controls.runButton().setVisible(false);
        controls.runButton().setManaged(false);
        controls.progressBar().setVisible(true);
        controls.progressBar().setManaged(true);
        controls.progressLabel().setVisible(true);
        controls.progressLabel().setManaged(true);
        controls.progressBar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        controls.progressLabel().setText(I18n.tr(CONFIG_DATABASE_SYNC_IN_PROGRESS));
        controls.resultTextArea().clear();
        controls.resultTextArea().setVisible(false);
        controls.resultTextArea().setManaged(false);
        popupStage.sizeToScene();

        Task<RemoteSyncExecutionResult> task = new Task<>() {
            @Override
            protected RemoteSyncExecutionResult call() throws Exception {
                RemoteSyncOptions options = new RemoteSyncOptions(syncConfiguration, syncExternalPlayerPaths);
                if (importMode) {
                    return remoteSyncClientService.importFromRemote(
                            normalizedHost,
                            port,
                            options,
                            (step, detail) -> updateMessage(formatRemoteSyncProgressMessage(step, detail))
                    );
                }
                return remoteSyncClientService.exportToRemote(
                        normalizedHost,
                        port,
                        options,
                        (step, detail) -> updateMessage(formatRemoteSyncProgressMessage(step, detail))
                );
            }
        };

        controls.progressLabel().textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            controls.progressLabel().textProperty().unbind();
            controls.syncRunning().set(false);
            applyPostDatabaseImport(importMode, previousConfiguration, syncConfiguration);
            refreshAppDataAfterDatabaseChange(importMode);
            controls.progressBar().setProgress(1);
            controls.progressLabel().setText(I18n.tr(importMode ? "configImportDatabaseSuccess" : "configExportDatabaseSuccess"));
            controls.resultTextArea().setText(buildRemoteDatabaseSyncSummary(importMode, task.getValue()));
            controls.resultTextArea().setVisible(true);
            controls.resultTextArea().setManaged(true);
            controls.cancelButton().setDisable(false);
            popupStage.sizeToScene();
            showMessageAlert(I18n.tr("remoteSyncRemoteCompletedMessage"));
        });
        task.setOnFailed(event -> {
            controls.progressLabel().textProperty().unbind();
            controls.syncRunning().set(false);
            controls.progressBar().setProgress(1);
            controls.progressLabel().setText(I18n.tr(importMode ? "configImportDatabaseFailed" : "configExportDatabaseFailed"));
            controls.resultTextArea().setText(I18n.tr("configDatabaseSyncFailedWithReason",
                    I18n.tr(databaseSyncActionKey(importMode)),
                    summarizeExceptionMessage(task.getException())));
            controls.resultTextArea().setVisible(true);
            controls.resultTextArea().setManaged(true);
            controls.cancelButton().setDisable(false);
            popupStage.sizeToScene();
            showErrorAlert(I18n.tr("remoteSyncRemoteFailedMessage"));
        });

        Thread worker = new Thread(task, importMode ? "remote-database-import-task" : "remote-database-export-task");
        worker.setDaemon(true);
        worker.start();
    }

    private void setDatabaseSyncControlsDisabled(boolean disabled, DatabaseSyncDialogControls controls) {
        controls.databasePathField().setDisable(disabled);
        controls.remoteHostField().setDisable(disabled);
        controls.remotePortField().setDisable(disabled);
        controls.browseButton().setDisable(disabled);
        controls.testConnectionButton().setDisable(disabled);
        controls.syncConfigurationCheckBox().setDisable(disabled);
        controls.runButton().setDisable(disabled);
    }

    private String normalizeSelectedPath(String selectedPath) {
        return selectedPath == null ? "" : selectedPath.trim();
    }

    private void configureDatabaseBackupFileChooser(boolean restoreMode) {
        databaseFileChooser.setTitle(I18n.tr("configSelectDatabaseFile"));
        databaseFileChooser.setInitialFileName(restoreMode
                ? ""
                : DatabaseBackupArchiveService.defaultFileName(System.currentTimeMillis() / 1000L));
    }

    private File ensureZipExtension(File selectedFile) {
        String normalizedPath = ensureZipExtension(selectedFile.getAbsolutePath());
        return normalizedPath.equals(selectedFile.getAbsolutePath()) ? selectedFile : new File(normalizedPath);
    }

    private String ensureZipExtension(String selectedPath) {
        String normalizedPath = normalizeSelectedPath(selectedPath);
        return normalizedPath.toLowerCase().endsWith(".zip") ? normalizedPath : normalizedPath + ".zip";
    }

    private boolean isMissingDatabasePath(String normalizedPath) {
        return normalizedPath.isBlank();
    }

    private boolean isMissingImportSource(boolean importMode, String normalizedPath) {
        return importMode && !new File(normalizedPath).exists();
    }

    private void applyPostDatabaseImport(boolean importMode,
                                         Configuration previousConfiguration,
                                         boolean syncConfiguration) {
        if (!importMode) {
            return;
        }
        Configuration currentConfiguration = service.read();
        I18n.setLocale(currentConfiguration.getLanguageLocale());
        applyPostSaveEffects(previousConfiguration, currentConfiguration);
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
        if (syncConfiguration && restartRequired(previousConfiguration, currentConfiguration)) {
            showMessageAlert(I18n.tr(CONFIG_EMBED_PLAYER_RESTART_NEEDED));
        }
    }

    private record DatabaseSyncDialogControls(TextField databasePathField,
                                              TextField remoteHostField,
                                              TextField remotePortField,
                                              Button browseButton,
                                              Button testConnectionButton,
                                              CheckBox syncConfigurationCheckBox,
                                              CheckBox syncExternalPlayerPathsCheckBox,
                                              Button runButton,
                                              Button cancelButton,
                                              ProgressBar progressBar,
                                              Label progressLabel,
                                              TextArea resultTextArea,
                                              AtomicBoolean syncRunning) {
    }

    private record DatabaseSyncRunRequest(Stage popupStage,
                                          boolean importMode,
                                          boolean fileMode,
                                          String selectedPath,
                                          String remoteHost,
                                          String remotePort,
                                          boolean syncConfiguration,
                                          boolean syncExternalPlayerPaths,
                                          DatabaseSyncDialogControls controls) {
    }

    private String buildDatabaseSyncSummary(boolean importMode, DatabaseSyncService.DatabaseSyncReport report) {
        StringBuilder summary = new StringBuilder(I18n.tr(databaseSyncResultKey(importMode, "Success")));
        summary.append("\n\n").append(I18n.tr("configDatabaseSyncSummaryRows", report.getTotalRowsSynced()));
        List<DatabaseSyncService.TableSyncResult> tableResults = report.getTableResults();
        for (DatabaseSyncService.TableSyncResult tableResult : tableResults) {
            summary.append("\n")
                    .append(I18n.tr("configDatabaseSyncSummaryTable", tableResult.getTableName(), tableResult.getRowCount()));
        }
        if (report.isConfigurationRequested()) {
            String configurationLineKey = report.isConfigurationCopied()
                    ? "configDatabaseSyncSummaryConfiguration"
                    : "configDatabaseSyncSummaryConfigurationMissing";
            String playerPathPolicyKey = report.isExternalPlayerPathsIncluded()
                    ? "configDatabaseSyncSummaryExternalPlayerPathsIncluded"
                    : "configDatabaseSyncSummaryExternalPlayerPathsKept";
            summary.append("\n")
                    .append(I18n.tr(configurationLineKey, I18n.tr(playerPathPolicyKey)));
        }
        return summary.toString();
    }

    private String buildDatabaseBackupSummary(boolean restoreMode, DatabaseBackupArchiveService.BackupArchiveReport report) {
        if (restoreMode) {
            return I18n.tr(
                    "configDatabaseRestoreSummary",
                    report.path(),
                    DatabaseBackupArchiveService.sizeLabel(report.databaseBytes())
            );
        }
        return I18n.tr(
                "configDatabaseBackupSummary",
                report.path(),
                DatabaseBackupArchiveService.sizeLabel(report.databaseBytes()),
                DatabaseBackupArchiveService.sizeLabel(report.archiveBytes())
        );
    }

    private String buildRemoteDatabaseSyncSummary(boolean importMode, RemoteSyncExecutionResult result) {
        if (result == null) {
            return I18n.tr(databaseSyncResultKey(importMode, "Success"));
        }
        if (result.report() == null) {
            return result.message();
        }
        String summary = buildDatabaseSyncSummary(importMode, result.report());
        if (result.message() == null || result.message().isBlank()) {
            return summary;
        }
        return summary + "\n\n" + result.message();
    }

    private void testRemoteDatabaseConnection(String host, String portText, Button testConnectionButton) {
        String normalizedHost = normalizeSelectedPath(host);
        String normalizedPort = normalizeSelectedPath(portText);
        if (normalizedHost.isBlank()) {
            showErrorAlert(I18n.tr("configDatabaseSyncRemoteHostRequired"));
            return;
        }
        if (normalizedPort.isBlank()) {
            showErrorAlert(I18n.tr("configDatabaseSyncRemotePortRequired"));
            return;
        }
        int port = parseRemotePort(normalizedPort);
        if (port <= 0) {
            showErrorAlert(I18n.tr("configDatabaseSyncRemotePortInvalid"));
            return;
        }
        testConnectionButton.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                remoteSyncClientService.checkConnection(normalizedHost, port);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            testConnectionButton.setDisable(false);
            showMessageAlert(I18n.tr("configDatabaseSyncConnectionSuccess"));
        });
        task.setOnFailed(event -> {
            testConnectionButton.setDisable(false);
            showErrorAlert(I18n.tr("configDatabaseSyncConnectionFailed") + "\n" + summarizeExceptionMessage(task.getException()));
        });
        Thread worker = new Thread(task, "remote-database-connection-test");
        worker.setDaemon(true);
        worker.start();
    }

    private int parseRemotePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port > 0 && port <= 65_535 ? port : -1;
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    private String formatRemoteSyncProgressMessage(RemoteSyncProgressStep step, String detail) {
        if (step == null) {
            return I18n.tr(CONFIG_DATABASE_SYNC_IN_PROGRESS);
        }
        return switch (step) {
            case CONNECTING -> I18n.tr("remoteSyncConnecting");
            case WAITING_FOR_APPROVAL -> I18n.tr("remoteSyncWaitingForApproval", detail == null ? "" : detail);
            case CREATING_SNAPSHOT -> I18n.tr("remoteSyncCreatingSnapshot");
            case UPLOADING -> I18n.tr("remoteSyncUploading");
            case PREPARING_DOWNLOAD -> I18n.tr("remoteSyncPreparingDownload");
            case DOWNLOADING -> I18n.tr("remoteSyncDownloading");
            case APPLYING_SYNC -> I18n.tr("remoteSyncApplyingSync");
            case COMPLETING_REMOTE -> I18n.tr("remoteSyncCompletingRemote");
            case FINISHED -> I18n.tr("remoteSyncFinished");
        };
    }

    private void bindManagedVisibility(Node node, javafx.beans.value.ObservableValue<Boolean> visibleProperty) {
        node.visibleProperty().bind(visibleProperty);
        node.managedProperty().bind(visibleProperty);
    }

    private void refreshAppDataAfterDatabaseChange(boolean importMode) {
        if (!didLocalDatabaseChange(importMode)) {
            return;
        }
        AppDataRefreshService.getInstance().refreshAfterDatabaseChange();
    }

    private boolean didLocalDatabaseChange(boolean importMode) {
        return importMode;
    }

    private String summarizeExceptionMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return I18n.tr("commonError");
        }
        return throwable.getMessage();
    }

    private String databaseSyncActionKey(boolean importMode) {
        return importMode ? CONFIG_IMPORT_DATABASE : CONFIG_EXPORT_DATABASE;
    }

    private String databaseSyncResultKey(boolean importMode, String suffix) {
        return databaseSyncActionKey(importMode) + suffix;
    }

    static String resolveChainHelpText() {
        return I18n.tr("configResolveChainAndDeepRedirectsHelp");
    }

    static String resolveChainHelpTitle() {
        return I18n.tr("configResolveChainAndDeepRedirectsHelpTitle");
    }

    static String wideViewHelpText() {
        return I18n.tr("configWideViewHelp");
    }

    static String wideViewHelpTitle() {
        return I18n.tr("configWideViewHelpTitle");
    }

    static String videoPlayersHelpText() {
        return I18n.tr("configVideoPlayersHelp");
    }

    static String videoPlayersHelpTitle() {
        return I18n.tr("configVideoPlayersHelpTitle");
    }

    static String themeHelpText() {
        return I18n.tr("configThemeHelp");
    }

    static String themeHelpTitle() {
        return I18n.tr("configThemeHelpTitle");
    }

    static String databaseSyncHelpText() {
        return I18n.tr("configDatabaseSyncHelpText");
    }

    static String databaseSyncHelpTitle() {
        return I18n.tr("configDatabaseSyncHelpTitle");
    }

    static String filtersHelpText() {
        return I18n.tr("configFiltersHelpText");
    }

    static String filtersHelpTitle() {
        return I18n.tr("configFiltersHelpTitle");
    }

    static String cacheFilteringHelpText() {
        return I18n.tr("configCacheFilteringHelpText");
    }

    static String cacheFilteringHelpTitle() {
        return I18n.tr("configCacheFilteringHelpTitle");
    }

    static String webServerHelpText() {
        return I18n.tr("configWebServerHelpText");
    }

    static String webServerHelpTitle() {
        return I18n.tr("configWebServerHelpTitle");
    }

    static String tmdbMetadataHelpText() {
        return I18n.tr("configTmdbMetadataHelpText");
    }

    static String tmdbMetadataHelpTitle() {
        return I18n.tr("configTmdbMetadataHelpTitle");
    }

    private void showResolveChainHelp() {
        showHelpDialog(resolveChainHelpTitle(), resolveChainHelpText());
    }

    private void showWideViewHelp() {
        showHelpDialog(wideViewHelpTitle(), wideViewHelpText());
    }

    private void showVideoPlayersHelp() {
        showHelpDialog(videoPlayersHelpTitle(), videoPlayersHelpText());
    }

    private void showThemeHelp() {
        showHelpDialog(themeHelpTitle(), themeHelpText());
    }

    private void showDatabaseSyncHelp() {
        showHelpDialog(databaseSyncHelpTitle(), databaseSyncHelpText());
    }

    private void showFiltersHelp() {
        showHelpDialog(filtersHelpTitle(), filtersHelpText());
    }

    private void showCacheFilteringHelp() {
        showHelpDialog(cacheFilteringHelpTitle(), cacheFilteringHelpText());
    }

    private void showWebServerHelp() {
        showHelpDialog(webServerHelpTitle(), webServerHelpText());
    }

    private void showTmdbMetadataHelp() {
        showHelpDialog(tmdbMetadataHelpTitle(), tmdbMetadataHelpText());
    }

    private void showHelpDialog(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.tr(DIALOG_TITLE_COMMON_INFO));
        alert.setHeaderText(title);
        alert.setContentText(text);
        alert.initOwner(getScene() == null ? null : getScene().getWindow());
        alert.initModality(javafx.stage.Modality.NONE);
        alert.getDialogPane().getStylesheets().add(RootApplication.getCurrentTheme());
        alert.showAndWait();
    }
    private record VlcCachingOption(String value, String label) {
        private static java.util.List<VlcCachingOption> all() {
            return java.util.List.of(
                    new VlcCachingOption("", I18n.tr("configVlcCachingDisabled")),
                    new VlcCachingOption("1000", I18n.tr("configVlcCaching1s")),
                    new VlcCachingOption("2000", I18n.tr("configVlcCaching2s")),
                    new VlcCachingOption("3000", I18n.tr("configVlcCaching3s")),
                    new VlcCachingOption("4000", I18n.tr("configVlcCaching4s")),
                    new VlcCachingOption("5000", I18n.tr("configVlcCaching5s")),
                    new VlcCachingOption("10000", I18n.tr("configVlcCaching10s")),
                    new VlcCachingOption("15000", I18n.tr("configVlcCaching15s")),
                    new VlcCachingOption("20000", I18n.tr("configVlcCaching20s")),
                    new VlcCachingOption("25000", I18n.tr("configVlcCaching25s")),
                    new VlcCachingOption("30000", I18n.tr("configVlcCaching30s")),
                    new VlcCachingOption("60000", I18n.tr("configVlcCaching60s"))
            );
        }

        private static VlcCachingOption fromValue(String value) {
            String normalized = ConfigurationService.getInstance().normalizeVlcCachingMs(value);
            for (VlcCachingOption option : all()) {
                if (Objects.equals(option.value(), normalized)) {
                    return option;
                }
            }
            return all().get(1);
        }
    }

    private void showSaveSuccessAnimation() {
        String originalText = saveButton.getText();
        saveButton.setText("✅");

        if (saveSuccessTimeline != null) {
            saveSuccessTimeline.stop();
        }

        saveSuccessTimeline = new Timeline(new KeyFrame(
                Duration.seconds(3),
                event -> {
                    saveButton.setText(originalText);
                    saveButton.setDisable(false);
                }
        ));
        saveSuccessTimeline.setCycleCount(1);
        saveSuccessTimeline.setOnFinished(event -> {
            saveButton.setText(originalText);
            saveButton.setDisable(false);
        });
        saveSuccessTimeline.play();
    }

    private void addBrowserButton1ClickHandler() {
        browserButtonPlayerPath1.setOnAction(_ -> {
            File file = fileChooser.showOpenDialog(RootApplication.getPrimaryStage());
            if (file != null) {
                playerPath1.setText(file.getAbsolutePath());
            }
        });
    }

    private void addBrowserButton2ClickHandler() {
        browserButtonPlayerPath2.setOnAction(_ -> {
            File file = fileChooser.showOpenDialog(RootApplication.getPrimaryStage());
            if (file != null) {
                playerPath2.setText(file.getAbsolutePath());
            }
        });
    }

    private void addBrowserButton3ClickHandler() {
        browserButtonPlayerPath3.setOnAction(_ -> {
            File file = fileChooser.showOpenDialog(RootApplication.getPrimaryStage());
            if (file != null) {
                playerPath3.setText(file.getAbsolutePath());
            }
        });
    }

    private void installPlayerSelectionConfirmationHandler() {
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (ignorePlayerSelectionPrompt || newToggle == null) {
                return;
            }
            if (newToggle == defaultWebBrowserPlayer) {
                boolean proceed = UIptvAlert.showConfirmationAlert(
                        I18n.tr("configBrowserCompatibilityWarning")
                );
                if (!proceed) {
                    restorePreviousPlayerSelection(oldToggle);
                }
                return;
            }
            if (newToggle == defaultEmbedPlayer) {
                boolean proceed = UIptvAlert.showConfirmationAlert(
                        I18n.tr("configEmbedPlayerVlcWarning")
                );
                if (!proceed) {
                    restorePreviousPlayerSelection(oldToggle);
                }
            }
        });
    }

    private void restorePreviousPlayerSelection(Toggle oldToggle) {
        ignorePlayerSelectionPrompt = true;
        try {
            group.selectToggle(oldToggle);
        } finally {
            ignorePlayerSelectionPrompt = false;
        }
    }

    private String sanitizeCacheExpiryDaysText() {
        int normalized = service.normalizeCacheExpiryDays(cacheExpiryDays.getText());
        String normalizedText = String.valueOf(normalized);
        if (!normalizedText.equals(cacheExpiryDays.getText())) {
            cacheExpiryDays.setText(normalizedText);
        }
        return normalizedText;
    }

    private int getSelectedThemeZoomPercent() {
        Integer selected = themeZoomComboBox.getSelectionModel().getSelectedItem();
        return selected == null ? ConfigurationService.DEFAULT_UI_ZOOM_PERCENT : selected;
    }

    private static final class StatusIcon extends StackPane {
        private final SVGPath glyph = new SVGPath();

        private StatusIcon() {
            getStyleClass().add(STYLE_CLASS_CONFIGURATION_STATUS_ICON);
            setMinSize(STATUS_ICON_SIZE, STATUS_ICON_SIZE);
            setPrefSize(STATUS_ICON_SIZE, STATUS_ICON_SIZE);
            setMaxSize(STATUS_ICON_SIZE, STATUS_ICON_SIZE);
            glyph.getStyleClass().add(STYLE_CLASS_CONFIGURATION_STATUS_GLYPH);
            getChildren().add(glyph);
            setEnabled(false);
        }

        private void setEnabled(boolean enabled) {
            glyph.setContent(enabled ? STATUS_ICON_CHECK_PATH : STATUS_ICON_CROSS_PATH);
            getStyleClass().removeAll(
                    STYLE_CLASS_CONFIGURATION_STATUS_ICON_ON,
                    STYLE_CLASS_CONFIGURATION_STATUS_ICON_OFF
            );
            getStyleClass().add(enabled
                    ? STYLE_CLASS_CONFIGURATION_STATUS_ICON_ON
                    : STYLE_CLASS_CONFIGURATION_STATUS_ICON_OFF);
        }
    }
}
