package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.player.api.VideoPlayerInterface;
import com.uiptv.service.*;
import com.uiptv.service.remotesync.RemoteSyncClientService;
import com.uiptv.service.remotesync.RemoteSyncExecutionResult;
import com.uiptv.service.remotesync.RemoteSyncOptions;
import com.uiptv.service.remotesync.RemoteSyncProgressStep;
import com.uiptv.ui.util.ImageCacheManager;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.ui.util.UiServerUrlUtil;
import com.uiptv.util.I18n;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.widget.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String BACKUP_CREATE_ICON_PATH = "M12 3 L19 10 H15 V17 H9 V10 H5 Z M5 19 H19 V21 H5 Z";
    private static final String BACKUP_RESTORE_ICON_PATH = "M12 5 C8.7 5 6 7.7 6 11 H3 L7 15 L11 11 H8 C8 8.8 9.8 7 12 7 C14.2 7 16 8.8 16 11 C16 13.2 14.2 15 12 15 C10.9 15 9.9 14.6 9.2 13.8 L7.8 15.2 C8.9 16.3 10.4 17 12 17 C15.3 17 18 14.3 18 11 C18 7.7 15.3 5 12 5 Z";
    private static final double STATUS_ICON_SIZE = 18;
    private static final Duration STATUS_TITLE_REFRESH_INTERVAL = Duration.seconds(30);
    private static final Duration AUTO_SAVE_DEBOUNCE = Duration.millis(450);
    private static final double DATABASE_SYNC_INLINE_WIDTH = 672;
    private static final double SETTINGS_CARD_WIDTH = 440;
    private static final double SETTINGS_MIN_COMPACT_CARD_WIDTH = 280;
    private static final double SETTINGS_CARD_HGAP = 14;
    private static final double SETTINGS_SWITCH_WIDTH = 52;
    private static final double SETTINGS_THEME_PILL_WIDTH = 181;
    private static final AtomicReference<Stage> activePublishM3u8PopupStage = new AtomicReference<>();
    private static final AtomicReference<Stage> activeVlcOptionsPopupStage = new AtomicReference<>();
    private static final AtomicReference<Stage> activeDatabaseSyncPopupStage = new AtomicReference<>();
    final ToggleGroup group = new ToggleGroup();
    final Button browserButtonPlayerPath1 = new Button("...");
    final Button browserButtonPlayerPath2 = new Button("...");
    final Button browserButtonPlayerPath3 = new Button("...");
    final FileChooser fileChooser = new FileChooser();
    private final VBox contentContainer = new VBox();
    private final PauseTransition autoSaveDebounce = new PauseTransition(AUTO_SAVE_DEBOUNCE);
    private final TextField settingsSearchTextField = new TextField();
    private final HBox searchRow = new HBox(8);
    private final PillBar<SettingsPanelFilter> settingsPillBar =
            new PillBar<>(SettingsPanelFilter::title, SettingsPanelFilter::id);
    private final PillBar<ThemeModeOption> themeModePillBar =
            new PillBar<>(ThemeModeOption::title, ThemeModeOption::id);
    private final SwitchToggle thumbnailModeSwitch = new SwitchToggle();
    private final SwitchToggle filterPasswordProtectionSwitch = new SwitchToggle();
    private final SwitchToggle wideViewSwitch = new SwitchToggle();
    private final SwitchToggle resolveChainAndDeepRedirectsSwitch = new SwitchToggle();
    private final FlowPane settingsCardPane = new FlowPane();
    private final List<SettingsSection> settingsSections = new ArrayList<>();
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
    private final Label filterLockStatusLabel = new Label();
    private final Label filtersGroupTitleLabel = new Label();
    private final StatusIcon filtersGroupStatusIcon = new StatusIcon();
    private final Label cacheFilteringGroupTitleLabel = new Label();
    private final Button filterLockPasswordButton = new Button();
    private final SwitchToggle filterLockStateSwitch = new SwitchToggle();
    private final Label filterLockStateTitleLabel = new Label(I18n.tr("filterLockStateToggleLabel"));
    private final Label filterLockStateValueLabel = new Label();
    private final ComboBox<Integer> filterLockUnlockDurationComboBox = new ComboBox<>();
    private Node filterLockStateRow;
    private HBox filterLockDurationRow;
    private Node filterPasswordProtectionRow;
    private Node wideViewRow;
    private final VBox filterAdminControls = new VBox(10);
    private final CheckBox darkThemeCheckBox = new CheckBox(I18n.tr("configUseDarkTheme"));
    private final SwitchToggle autoRunServerOnStartupSwitch = new SwitchToggle();
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
    private final Hyperlink importDatabaseButton = new Hyperlink(I18n.tr(CONFIG_IMPORT_DATABASE));
    private final Hyperlink exportDatabaseButton = new Hyperlink(I18n.tr(CONFIG_EXPORT_DATABASE));
    private final FileChooser databaseFileChooser = new FileChooser();
    private final Callback<Object> onSaveCallback;
    private final ConfigurationService service = ConfigurationService.getInstance();
    private final ConfigurationApplicationService configurationApplicationService = ConfigurationApplicationService.getInstance();
    private final CacheService cacheService = new CacheServiceImpl();
    private final RemoteSyncClientService remoteSyncClientService = new RemoteSyncClientService();
    private final DatabaseBackupArchiveService databaseBackupArchiveService = DatabaseBackupArchiveService.getInstance();
    private final HostServices hostServices;
    private final Runnable themeToggleHandler;
    private String dbId;
    private String persistedFilterCategoriesValue = "";
    private String persistedFilterChannelsValue = "";
    private boolean persistedPauseFilteringValue = false;
    private boolean ignorePlayerSelectionPrompt = false;
    private String vlcNetworkCachingMs = ConfigurationService.DEFAULT_VLC_CACHING_MS;
    private String vlcLiveCachingMs = ConfigurationService.DEFAULT_VLC_CACHING_MS;
    private boolean vlcHttpUserAgentEnabled = true;
    private boolean vlcHttpForwardCookiesEnabled = true;
    private boolean vlcNoVideoTitleShow = true;
    private boolean vlcQuiet = true;
    private boolean vlcHttpReconnect = true;
    private boolean vlcAdaptiveUseAccess = true;
    private boolean vlcVoutEnabled = false;
    private boolean vlcAvcodecHwEnabled = false;
    private boolean syncingThemeModeSelector;
    private boolean syncingThumbnailModeSelector;
    private boolean syncingPasswordProtectionSelector;
    private boolean syncingFilterLockStateSwitch;
    private boolean syncingConfigurationToForm;
    private boolean savingConfiguration;
    private boolean playerSelectionConfirmationActive;
    private boolean playerSelectionSaveDeferred;
    private boolean playerOptionSwitchesConfigured;
    private PlayerOptionCard embeddedPlayerCard;
    private AppHeaderActions pageHeaderActions;
    @SuppressWarnings("java:S1450")
    private Timeline serverStatusTimeline;
    @SuppressWarnings("java:S1450")
    private Timeline statusTitleTimeline;
    private final ConfigurationChangeListener configurationChangeListener = _ -> Platform.runLater(() -> {
        if (!savingConfiguration) {
            refreshConfigurationForm();
        }
    });
    private final FilterLockService.LockStateChangeListener filterLockStateChangeListener = this::scheduleFilterLockUiRefresh;

    public ConfigurationUI(Callback<Object> onSaveCallback) {
        this(onSaveCallback, null, null);
    }

    public ConfigurationUI(Callback<Object> onSaveCallback, HostServices hostServices, Runnable themeToggleHandler) {
        this.onSaveCallback = onSaveCallback;
        this.hostServices = hostServices;
        this.themeToggleHandler = themeToggleHandler;
        initWidgets();
    }

    private record SettingsPanelFilter(String id, String title) {
    }

    private record ThemeModeOption(String id, String title, boolean dark) {
    }

    private record SettingsSection(String id, String title, Node statusIcon, Node content, Hyperlink helpLink,
                                   String searchText) {
    }

    static void clearWatchingNowStates() {
        ConfigurationApplicationService.getInstance().clearWatchingNowState();
    }

    private void initWidgets() {
        getStyleClass().add("settings-page-root");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeLayout(contentContainer);
        UiRenderQuality.optimizeLayout(settingsCardPane);
        UiRenderQuality.optimizeTextNode(settingsSearchTextField);
        setPadding(Insets.EMPTY);
        setSpacing(0);
        startServerButton.getStyleClass().add(STYLE_CLASS_NO_DIM_DISABLED);
        contentContainer.getStyleClass().add("settings-page");
        contentContainer.setPadding(Insets.EMPTY);
        contentContainer.setSpacing(12);
        contentContainer.setMinSize(0, 0);
        contentContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        settingsSearchTextField.setPromptText(I18n.tr("commonSearch"));
        SearchFieldBehavior.installMouseClear(settingsSearchTextField);
        searchRow.getChildren().setAll(settingsSearchTextField);
        searchRow.getStyleClass().add("search-row");
        searchRow.setMinWidth(0);
        searchRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(settingsSearchTextField, Priority.ALWAYS);
        settingsSearchTextField.textProperty().addListener((_, _, _) -> refreshSettingsCards());
        settingsPillBar.setNarrowReservedRowCount(3);
        settingsPillBar.setMaxWidth(Double.MAX_VALUE);
        settingsCardPane.getStyleClass().add("settings-section-grid");
        settingsCardPane.setHgap(SETTINGS_CARD_HGAP);
        settingsCardPane.setVgap(14);
        settingsCardPane.setMinWidth(0);
        settingsCardPane.setMaxWidth(Double.MAX_VALUE);
        settingsCardPane.setPrefWrapLength(SETTINGS_CARD_WIDTH * 3 + SETTINGS_CARD_HGAP * 2);
        contentContainer.widthProperty().addListener((_, _, _) -> updateSettingsCardWidths());
        settingsCardPane.widthProperty().addListener((_, _, _) -> updateSettingsCardWidths());
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
        scrollPane.setFocusTraversable(false);
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
        playerPath1.setMaxWidth(Double.MAX_VALUE);
        playerPath2.setMaxWidth(Double.MAX_VALUE);
        playerPath3.setMaxWidth(Double.MAX_VALUE);
        filterCategoriesWithTextContains.setMinWidth(0);
        filterChannelWithTextContains.setMinWidth(0);
        filterCategoriesWithTextContains.setMaxWidth(Double.MAX_VALUE);
        filterChannelWithTextContains.setMaxWidth(Double.MAX_VALUE);
        filterCategoriesWithTextContains.setPrefRowCount(6);
        filterChannelWithTextContains.setPrefRowCount(6);
        filterCategoriesWithTextContains.getStyleClass().add("settings-filter-text-area");
        filterChannelWithTextContains.getStyleClass().add("settings-filter-text-area");
        registerConfigurationChangeListener();

        filterLockStatusLabel.setWrapText(true);
        filterLockStatusLabel.getStyleClass().add(STYLE_CLASS_DIM_LABEL);
        configureFilterPasswordProtectionSelector();
        cacheExpiryDays.getStyleClass().add("settings-numeric-field");
        cacheExpiryDays.setAlignment(Pos.CENTER);
        cacheExpiryDays.setPrefColumnCount(5);
        cacheExpiryDays.setMinWidth(92);
        cacheExpiryDays.setPrefWidth(92);
        cacheExpiryDays.setMaxWidth(92);
        Label cacheExpiryLabel = new Label(I18n.tr("configCacheExpiresInDays"));
        cacheExpiryLabel.setMinWidth(0);
        cacheExpiryLabel.setMaxWidth(Double.MAX_VALUE);
        cacheExpiryLabel.setWrapText(true);
        HBox.setHgrow(cacheExpiryLabel, Priority.ALWAYS);
        HBox cacheExpiryRow = new HBox(10, cacheExpiryLabel, cacheExpiryDays);
        cacheExpiryRow.setAlignment(Pos.CENTER_LEFT);
        cacheExpiryRow.setMinWidth(0);
        cacheExpiryRow.setMaxWidth(Double.MAX_VALUE);
        fileChooser.setTitle(I18n.tr("configSelectStreamingPlayer"));
        tmdbReadAccessToken.setPromptText(I18n.tr("configTmdbReadAccessTokenPrompt"));
        tmdbReadAccessToken.setMinWidth(0);
        tmdbReadAccessToken.setPrefWidth(295);
        tmdbReadAccessToken.setMaxWidth(Double.MAX_VALUE);
        configurePlayerOptionSwitches();
        wideViewRow = createSettingSwitchHelpRow("configWideView", wideViewSwitch, wideViewHelpLink);
        Node resolveChainRow = createSettingSwitchHelpRow("configResolveChainAndDeepRedirects", resolveChainAndDeepRedirectsSwitch, resolveChainAndDeepRedirectsHelpLink);
        updateWideViewVisibility();
        Label tmdbTokenLabel = new Label(I18n.tr("configTmdbReadAccessToken"));
        FlowPane tmdbLinksRow = createWrappingRow(10, 4, tmdbApiGuideLink, tmdbApiKeyPageLink);
        VBox tmdbConfigSection = new VBox(6, tmdbTokenLabel, tmdbReadAccessToken, tmdbLinksRow);
        tmdbConfigSection.getStyleClass().add(STYLE_CLASS_OUTLINE_PANE);
        VBox playersGroup = new VBox(12, createPlayerChoicesPanel(), wideViewRow, resolveChainRow);
        resolveChainAndDeepRedirectsHelpLink.getStyleClass().add(STYLE_CLASS_NO_DIM_DISABLED);
        wideViewHelpLink.getStyleClass().add(STYLE_CLASS_NO_DIM_DISABLED);

        filterAdminControls.getChildren().setAll(filterCategoriesWithTextContains, filterChannelWithTextContains);
        filterLockStateRow = createFilterLockStateRow();
        filterLockPasswordButton.getStyleClass().add("settings-filter-password-button");
        filterLockDurationRow = createFilterLockDurationRow();
        filterPasswordProtectionRow = createFilterPasswordProtectionRow();
        VBox filtersGroup = new VBox(10, filterLockStatusLabel, filterLockStateRow, filterPasswordProtectionRow, filterLockDurationRow, filterAdminControls, filterLockPasswordButton);

        VBox themeOverridesGroup = buildThemeOverrideGroup();

        FlowPane clearButtons = createWrappingRow(10, 6, clearCacheButton, clearWatchingNowButton);
        reloadCacheButton.setMaxWidth(Region.USE_PREF_SIZE);
        VBox cacheGroup = new VBox(10, cacheExpiryRow, clearButtons, reloadCacheButton);
        refreshConfigurationBlockTitles();

        openServerLink.setVisible(false);
        openServerLink.setManaged(false);
        openSecureServerLink.setVisible(false);
        openSecureServerLink.setManaged(false);
        serverPort.setMaxWidth(Double.MAX_VALUE);
        httpsServerPort.setMaxWidth(Double.MAX_VALUE);
        openServerLink.getStyleClass().add("settings-server-open-link");
        openSecureServerLink.getStyleClass().add("settings-server-open-link");
        startServerButton.getStyleClass().add("settings-server-start-button");
        publishM3u8Button.getStyleClass().add("settings-server-publish-button");
        publishM3u8Button.setMaxWidth(Region.USE_PREF_SIZE);
        publishM3u8Button.setPrefWidth(Region.USE_COMPUTED_SIZE);
        httpsServerPort.textProperty().addListener((_, _, _) -> refreshServerStatusUI());
        VBox serverGroup = new VBox(
                10,
                createServerPortRow("HTTP", serverPort, openServerLink),
                createServerPortRow("HTTPS", httpsServerPort, openSecureServerLink),
                createSettingSwitchRow("configAutoRunServerOnStartup", autoRunServerOnStartupSwitch),
                createServerActionRow()
        );
        serverGroup.setFillWidth(true);
        VBox databaseSyncGroup = buildDatabaseSyncGroup();

        pageHeaderActions = new AppHeaderActions(hostServices, this::toggleThemeFromHeader, this::refreshConfigurationForm);
        AppPageHeader pageHeader = new AppPageHeader(I18n.tr("autoSettings"), pageHeaderActions);
        pageHeader.setHeaderTitleVisible(false);
        pageHeader.setNavigationSelectionEnabled(false);
        settingsSections.clear();
        settingsSections.addAll(List.of(
                new SettingsSection("players", I18n.tr("configVideoPlayers"), null, playersGroup, videoPlayersHelpLink, "players video playback embedded vlc wide view redirects"),
                new SettingsSection("filters", I18n.tr("configFilters"), filtersGroupStatusIcon, filtersGroup, filtersHelpLink, "filters parental lock categories channels censor hidden protected"),
                new SettingsSection("appearance", I18n.tr("configDarkTheme"), null, themeOverridesGroup, themeHelpLink, "appearance theme language thumbnails zoom"),
                new SettingsSection("cache", I18n.tr("configCacheFiltering"), null, cacheGroup, cacheFilteringHelpLink, "cache clear reload watching now"),
                new SettingsSection("sync", I18n.tr("configDatabaseSyncTitle"), null, databaseSyncGroup, databaseSyncHelpLink, "database sync import export backup remote"),
                new SettingsSection("server", I18n.tr("configWebServer"), null, serverGroup, webServerHelpLink, "web server https port m3u publish startup"),
                new SettingsSection("tmdb", I18n.tr("configTmdbMetadata"), null, tmdbConfigSection, tmdbMetadataHelpLink, "tmdb metadata api token")
        ));
        configureSettingsPillBar();
        refreshSettingsCards();
        contentContainer.getChildren().setAll(pageHeader, settingsPillBar, searchRow, settingsCardPane);
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
        installAutoSaveHandlers();
        installPlayerSelectionConfirmationHandler();
        installServerStatusMonitor();
        installStatusTitleMonitor();
        refreshFilterLockUi();
    }

    private void configureSettingsPillBar() {
        List<SettingsPanelFilter> filters = new ArrayList<>();
        filters.add(new SettingsPanelFilter("all", I18n.tr("commonAll")));
        filters.addAll(settingsSections.stream()
                .map(section -> new SettingsPanelFilter(section.id(), section.title()))
                .toList());
        settingsPillBar.setItems(filters);
        settingsPillBar.selectedItemProperty().addListener((_, _, _) -> refreshSettingsCards());
    }

    private void refreshSettingsCards() {
        if (settingsCardPane == null || settingsSections.isEmpty()) {
            return;
        }
        String selectedFilterId = settingsPillBar.getSelectedItem() == null ? "all" : settingsPillBar.getSelectedItem().id();
        String query = settingsSearchTextField.getText() == null
                ? ""
                : settingsSearchTextField.getText().trim().toLowerCase(Locale.ROOT);
        settingsCardPane.getChildren().clear();
        for (SettingsSection section : settingsSections) {
            if (!"all".equals(selectedFilterId) && !section.id().equals(selectedFilterId)) {
                continue;
            }
            if (!query.isEmpty() && !matchesSettingsSearch(section, query)) {
                continue;
            }
            settingsCardPane.getChildren().add(createSettingsSectionCard(section));
        }
        updateSettingsCardWidths();
    }

    private boolean matchesSettingsSearch(SettingsSection section, String query) {
        return section.title().toLowerCase(Locale.ROOT).contains(query)
                || section.searchText().toLowerCase(Locale.ROOT).contains(query);
    }

    private VBox createSettingsSectionCard(SettingsSection section) {
        detachFromParent(section.content());
        VBox card = new VBox(12);
        card.getStyleClass().add("settings-section-card");
        UiRenderQuality.optimizeLayout(card);
        card.setMinWidth(SETTINGS_MIN_COMPACT_CARD_WIDTH);
        card.setPrefWidth(SETTINGS_CARD_WIDTH);
        card.setMaxWidth(SETTINGS_CARD_WIDTH);
        card.setFocusTraversable(false);

        Label titleLabel = new Label(section.title());
        titleLabel.getStyleClass().add("settings-section-title");
        titleLabel.setMinWidth(0);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setWrapText(true);
        UiRenderQuality.optimizeTextNode(titleLabel);
        HBox titleRow = new HBox(6, titleLabel);
        titleRow.getStyleClass().add("settings-section-card-header");
        UiRenderQuality.optimizeLayout(titleRow);
        titleRow.setAlignment(Pos.TOP_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        if (section.statusIcon() != null) {
            detachFromParent(section.statusIcon());
            titleRow.getChildren().add(section.statusIcon());
        }
        if (section.helpLink() != null) {
            detachFromParent(section.helpLink());
            titleRow.getChildren().add(section.helpLink());
        }

        card.getChildren().setAll(titleRow, section.content());
        return card;
    }

    private void updateSettingsCardWidths() {
        double availableWidth = getSettingsCardAvailableWidth();
        double cardWidth = availableWidth < SETTINGS_CARD_WIDTH + 8
                ? Math.max(SETTINGS_MIN_COMPACT_CARD_WIDTH, availableWidth)
                : SETTINGS_CARD_WIDTH;
        settingsCardPane.setPrefWrapLength(Math.max(1, availableWidth));
        for (Node node : settingsCardPane.getChildren()) {
            if (node instanceof Region region) {
                region.setMinWidth(Math.min(SETTINGS_MIN_COMPACT_CARD_WIDTH, cardWidth));
                region.setPrefWidth(cardWidth);
                region.setMaxWidth(cardWidth);
            }
        }
    }

    private double getSettingsCardAvailableWidth() {
        double availableWidth = settingsCardPane.getWidth();
        if (availableWidth <= 0) {
            availableWidth = contentContainer.getWidth();
            Insets contentInsets = contentContainer.getInsets();
            if (contentInsets != null) {
                availableWidth -= contentInsets.getLeft() + contentInsets.getRight();
            }
        }
        Insets cardPaneInsets = settingsCardPane.getInsets();
        if (cardPaneInsets != null) {
            availableWidth -= cardPaneInsets.getLeft() + cardPaneInsets.getRight();
        }
        if (availableWidth <= 0) {
            availableWidth = Math.max(0, getWidth() - 56);
        }
        return Math.max(0, availableWidth);
    }

    private void detachFromParent(Node node) {
        if (node == null || node.getParent() == null) {
            return;
        }
        if (node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }

    private void toggleThemeFromHeader() {
        if (themeToggleHandler != null) {
            themeToggleHandler.run();
            Configuration configuration = service.read();
            if (configuration != null) {
                darkThemeCheckBox.setSelected(configuration.isDarkTheme());
                syncThemeModeSelector();
            }
            return;
        }
        darkThemeCheckBox.setSelected(!darkThemeCheckBox.isSelected());
        syncThemeModeSelector();
        applyThemePreview();
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

    private Node createFilterLockStateRow() {
        filterLockStateSwitch.getStyleClass().add("filter-lock-state-switch");
        filterLockStateTitleLabel.getStyleClass().add("settings-filter-mode-label");
        filterLockStateValueLabel.getStyleClass().add("filter-lock-state-value");

        VBox labels = new VBox(2, filterLockStateTitleLabel, filterLockStateValueLabel);
        labels.setMinWidth(0);
        labels.setMaxWidth(Double.MAX_VALUE);

        HBox row = new HBox(12, labels, filterLockStateSwitch);
        row.getStyleClass().add("filter-lock-state-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(labels, Priority.ALWAYS);
        return row;
    }

    private Node createSettingSwitchRow(String labelKey, SwitchToggle switchToggle) {
        return createSettingControlRow(labelKey, switchToggle, SETTINGS_SWITCH_WIDTH);
    }

    private Node createSettingSwitchHelpRow(String labelKey, SwitchToggle switchToggle, Hyperlink helpLink) {
        Label label = new Label(I18n.tr(labelKey));
        label.getStyleClass().add("settings-filter-mode-label");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(false);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setAlignment(Pos.CENTER_LEFT);

        HBox labelBox = new HBox(4, label, helpLink);
        labelBox.setAlignment(Pos.CENTER_LEFT);
        labelBox.setMinWidth(0);
        labelBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);

        GridPane row = new GridPane();
        row.getStyleClass().add("settings-filter-mode-row");
        row.setHgap(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHgrow(Priority.ALWAYS);
        labelColumn.setFillWidth(true);
        labelColumn.setMinWidth(0);
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setMinWidth(SETTINGS_SWITCH_WIDTH);
        controlColumn.setPrefWidth(SETTINGS_SWITCH_WIDTH);
        controlColumn.setMaxWidth(SETTINGS_SWITCH_WIDTH);
        switchToggle.setMinWidth(SETTINGS_SWITCH_WIDTH);
        switchToggle.setPrefWidth(SETTINGS_SWITCH_WIDTH);
        switchToggle.setMaxWidth(SETTINGS_SWITCH_WIDTH);

        row.getColumnConstraints().addAll(labelColumn, controlColumn);
        row.add(labelBox, 0, 0);
        row.add(switchToggle, 1, 0);
        GridPane.setHgrow(labelBox, Priority.ALWAYS);
        return row;
    }

    private void configurePlayerOptionSwitches() {
        if (playerOptionSwitchesConfigured) {
            return;
        }
        wideViewSwitch.selectedProperty().bindBidirectional(wideViewCheckBox.selectedProperty());
        resolveChainAndDeepRedirectsSwitch.selectedProperty().bindBidirectional(resolveChainAndDeepRedirectsCheckBox.selectedProperty());
        playerOptionSwitchesConfigured = true;
    }

    private Node createServerPortRow(String labelText, TextInputControl portField, Hyperlink openLink) {
        Label label = new Label(labelText);
        label.getStyleClass().add("settings-server-port-label");
        label.setMinWidth(64);
        label.setPrefWidth(64);

        HBox row = new HBox(10, label, portField, openLink);
        row.getStyleClass().add("settings-server-port-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(portField, Priority.ALWAYS);
        return row;
    }

    private Node createServerActionRow() {
        FlowPane row = createWrappingRow(10, 6, startServerButton, publishM3u8Button);
        row.getStyleClass().add("settings-server-action-row");
        return row;
    }

    private FlowPane createWrappingRow(double hgap, double vgap, Node... children) {
        FlowPane row = new FlowPane();
        row.setHgap(hgap);
        row.setVgap(vgap);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().addAll(children);
        return row;
    }

    private Node createSettingPillRow(String labelKey, PillBar<?> pillBar) {
        return createSettingControlRow(labelKey, pillBar, SETTINGS_THEME_PILL_WIDTH);
    }

    private Node createSettingControlRow(String labelKey, Node control, double controlWidth) {
        Label label = new Label(I18n.tr(labelKey));
        label.getStyleClass().add("settings-filter-mode-label");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(false);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setAlignment(Pos.CENTER_LEFT);

        GridPane row = new GridPane();
        row.getStyleClass().add("settings-filter-mode-row");
        row.setHgap(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHgrow(Priority.ALWAYS);
        labelColumn.setFillWidth(true);
        labelColumn.setMinWidth(0);
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setMinWidth(controlWidth);
        controlColumn.setPrefWidth(controlWidth);
        controlColumn.setMaxWidth(controlWidth);
        if (control instanceof Region region) {
            region.setMinWidth(controlWidth);
            region.setPrefWidth(controlWidth);
            region.setMaxWidth(controlWidth);
        }
        row.getColumnConstraints().addAll(labelColumn, controlColumn);
        row.add(label, 0, 0);
        row.add(control, 1, 0);
        GridPane.setHgrow(label, Priority.ALWAYS);
        return row;
    }

    private Node createFilterPasswordProtectionRow() {
        Node row = createSettingSwitchRow("filterLockDisablePasswordAction", filterPasswordProtectionSwitch);
        row.setVisible(false);
        row.setManaged(false);
        return row;
    }

    private VBox buildThemeOverrideGroup() {
        configureThemeModeSelector();
        configureThumbnailModeSelector();
        Node themeModeRow = createSettingPillRow("configDarkTheme", themeModePillBar);

        languageComboBox.setMaxWidth(Double.MAX_VALUE);
        languageComboBox.setMinWidth(0);

        themeZoomComboBox.getStyleClass().add("uiptv-combo-box");
        themeZoomComboBox.setMaxWidth(Double.MAX_VALUE);
        themeZoomComboBox.setMinWidth(0);

        VBox languageAndZoomSection = new VBox(
                10,
                createStackedSettingControlRow("configLanguage", languageComboBox),
                createStackedSettingControlRow("configThemeZoom", themeZoomComboBox)
        );
        languageAndZoomSection.getStyleClass().add(STYLE_CLASS_OUTLINE_PANE);
        languageAndZoomSection.setMaxWidth(Double.MAX_VALUE);

        return new VBox(10, themeModeRow, createSettingSwitchRow("configPlainTextMode", thumbnailModeSwitch), languageAndZoomSection);
    }

    private Node createStackedSettingControlRow(String labelKey, Region control) {
        Label label = new Label(I18n.tr(labelKey));
        label.getStyleClass().add("settings-filter-mode-label");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
        UiRenderQuality.optimizeTextNode(label);

        control.setMinWidth(0);
        control.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(control, Priority.NEVER);

        VBox row = new VBox(5, label, control);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private void configureThumbnailModeSelector() {
        thumbnailModeSwitch.selectedProperty().addListener((_, _, selected) -> {
            if (syncingThumbnailModeSelector) {
                return;
            }
            enableThumbnailsCheckBox.setSelected(!selected);
        });
        syncThumbnailModeSelector();
    }

    private void configureFilterPasswordProtectionSelector() {
        filterPasswordProtectionSwitch.selectedProperty().addListener((_, _, selected) ->
                handleFilterPasswordProtectionSwitchChanged(selected));
        setFilterPasswordProtectionSelection(false);
    }

    private void handleFilterPasswordProtectionSwitchChanged(boolean disableRequested) {
        if (syncingConfigurationToForm || syncingPasswordProtectionSelector) {
            return;
        }
        if (!disableRequested) {
            return;
        }
        boolean disabled = FilterLockDialogs.openDisablePasswordDialog(this);
        setFilterPasswordProtectionSelection(false);
        if (disabled) {
            refreshConfigurationForm();
        } else {
            refreshFilterLockUi();
        }
    }

    private boolean isParentalLockRestrictionsPaused() {
        return !filterLockStateSwitch.isSelected();
    }

    private void setFilterPasswordProtectionSelection(boolean disableRequested) {
        syncingPasswordProtectionSelector = true;
        try {
            filterPasswordProtectionSwitch.setSelected(disableRequested);
        } finally {
            syncingPasswordProtectionSelector = false;
        }
    }

    private void syncFilterLockStateSwitch(boolean restrictionsActive) {
        syncingFilterLockStateSwitch = true;
        try {
            filterLockStateSwitch.setSelected(restrictionsActive);
        } finally {
            syncingFilterLockStateSwitch = false;
        }
    }

    private void configureThemeModeSelector() {
        themeModePillBar.setItems(List.of(createThemeModeOption(false), createThemeModeOption(true)));
        themeModePillBar.selectedItemProperty().addListener((_, _, selected) -> {
            if (syncingThemeModeSelector) {
                return;
            }
            if (selected == null) {
                return;
            }
            darkThemeCheckBox.setSelected(selected.dark());
            applyThemePreview();
            requestImmediateAutoSave("themeMode");
        });
        syncThemeModeSelector();
    }

    private ThemeModeOption createThemeModeOption(boolean dark) {
        return new ThemeModeOption(
                dark ? "dark" : "light",
                I18n.tr(dark ? "commonDark" : "commonLight"),
                dark
        );
    }

    private void syncThumbnailModeSelector() {
        syncingThumbnailModeSelector = true;
        try {
            thumbnailModeSwitch.setSelected(!enableThumbnailsCheckBox.isSelected());
        } finally {
            syncingThumbnailModeSelector = false;
        }
    }

    private void syncThemeModeSelector() {
        syncingThemeModeSelector = true;
        try {
            themeModePillBar.setSelectedItem(createThemeModeOption(darkThemeCheckBox.isSelected()));
        } finally {
            syncingThemeModeSelector = false;
        }
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

    private void installAutoSaveHandlers() {
        autoSaveDebounce.setOnFinished(_ -> Platform.runLater(() -> saveCurrentSettings(true)));

        installDebouncedAutoSave(playerPath1, "playerPath1");
        installDebouncedAutoSave(playerPath2, "playerPath2");
        installDebouncedAutoSave(playerPath3, "playerPath3");
        installDebouncedAutoSave(filterCategoriesWithTextContains, "filterCategories");
        installDebouncedAutoSave(filterChannelWithTextContains, "filterChannels");
        installDebouncedAutoSave(serverPort, "serverPort");
        installDebouncedAutoSave(httpsServerPort, "httpsServerPort");
        installDebouncedAutoSave(cacheExpiryDays, "cacheExpiryDays");
        installDebouncedAutoSave(tmdbReadAccessToken, "tmdbReadAccessToken");

        group.selectedToggleProperty().addListener((_, _, _) -> Platform.runLater(() -> {
            updateVlcOptionsLinkVisibility();
            updateWideViewVisibility();
            if (ignorePlayerSelectionPrompt) {
                return;
            }
            if (playerSelectionConfirmationActive) {
                playerSelectionSaveDeferred = true;
                return;
            }
            requestImmediateAutoSave("defaultPlayer");
        }));
        installImmediateAutoSave(wideViewCheckBox, "wideView");
        installImmediateAutoSave(resolveChainAndDeepRedirectsCheckBox, "resolveRedirects");
        installImmediateAutoSave(enableThumbnailsCheckBox, "enableThumbnails");
        autoRunServerOnStartupSwitch.selectedProperty().addListener((_, _, _) -> requestImmediateAutoSave("autoRunServerOnStartup"));

        languageComboBox.valueProperty().addListener((_, _, _) -> requestImmediateAutoSave("language"));
        themeZoomComboBox.valueProperty().addListener((_, _, _) -> requestImmediateAutoSave("themeZoom"));
        filterLockUnlockDurationComboBox.valueProperty().addListener((_, _, _) -> requestImmediateAutoSave("filterLockDuration"));
    }

    private void installDebouncedAutoSave(TextInputControl control, String reason) {
        control.textProperty().addListener((_, _, _) -> requestDebouncedAutoSave(reason));
    }

    private void installImmediateAutoSave(CheckBox checkBox, String reason) {
        checkBox.selectedProperty().addListener((_, _, _) -> requestImmediateAutoSave(reason));
    }

    private void requestDebouncedAutoSave(String reason) {
        if (isAutoSaveSuppressed()) {
            autoSaveDebounce.stop();
            return;
        }
        autoSaveDebounce.playFromStart();
    }

    private void requestImmediateAutoSave(String reason) {
        if (isAutoSaveSuppressed()) {
            autoSaveDebounce.stop();
            return;
        }
        autoSaveDebounce.stop();
        saveCurrentSettings(true);
    }

    private boolean isAutoSaveSuppressed() {
        return syncingConfigurationToForm
                || savingConfiguration
                || syncingThemeModeSelector
                || syncingThumbnailModeSelector
                || syncingPasswordProtectionSelector
                || syncingFilterLockStateSwitch;
    }

    private void runWithAutoSaveSuppressed(Runnable action) {
        if (action == null) {
            return;
        }
        boolean wasSyncingConfigurationToForm = syncingConfigurationToForm;
        syncingConfigurationToForm = true;
        autoSaveDebounce.stop();
        try {
            action.run();
        } finally {
            syncingConfigurationToForm = wasSyncingConfigurationToForm;
        }
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
        reloadCacheButton.setOnAction(event -> ReloadCachePopup.showPopup(resolveOwnerStage(), null, this::notifyAccountsChanged));
    }

    private void notifyAccountsChanged() {
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
    }

    private Stage resolveOwnerStage() {
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return RootApplication.getPrimaryStage();
    }

    private Stage createPopupStage(String title) {
        Stage popupStage = new Stage();
        Stage owner = resolveOwnerStage();
        if (owner != null) {
            popupStage.initOwner(owner);
            popupStage.initModality(Modality.WINDOW_MODAL);
        } else {
            popupStage.initModality(Modality.APPLICATION_MODAL);
        }
        popupStage.setTitle(title);
        return popupStage;
    }

    private Scene createPopupScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        UiI18n.applySceneOrientation(scene);
        if (getScene() != null) {
            scene.getStylesheets().addAll(getScene().getStylesheets());
        } else if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
        return scene;
    }

    private void updateEmbeddedPlayerTitle() {
        String title = resolveEmbeddedPlayerTitle();
        if (embeddedPlayerCard != null) {
            embeddedPlayerCard.setTitle(title);
            return;
        }
        defaultEmbedPlayer.setText(title);
    }

    private String resolveEmbeddedPlayerTitle() {
        VideoPlayerInterface.PlayerType playerType = MediaPlayerFactory.getPlayerType();
        String title = I18n.tr("configEmbeddedPlayer");
        if (playerType == VideoPlayerInterface.PlayerType.VLC) {
            title = I18n.tr("configEmbeddedPlayerVlc");
        }
        return title;
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
                    saveCurrentSettings(false);
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
            Stage activeStage = activePublishM3u8PopupStage.get();
            if (activeStage != null && activeStage.isShowing()) {
                activeStage.toFront();
                activeStage.requestFocus();
                return;
            }
            Stage popupStage = createPopupStage(I18n.tr("configPublishM3u8"));
            M3U8PublicationPopup popup = new M3U8PublicationPopup(popupStage);
            popupStage.setScene(createPopupScene(popup, 680, 560));
            popupStage.setOnHidden(e -> activePublishM3u8PopupStage.compareAndSet(popupStage, null));
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
                    clearConfigurationState();
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
                    clearConfigurationState();
                }
            } else if (statusTitleTimeline != null) {
                statusTitleTimeline.play();
                refreshConfigurationBlockTitles();
            }
        });
    }

    private void refreshConfigurationBlockTitles() {
        Configuration configuration = service.read();
        boolean restrictionsActive = configuration == null || !configuration.isPauseFiltering();
        filtersGroupTitleLabel.setText(I18n.tr("configFilters"));
        cacheFilteringGroupTitleLabel.setText(I18n.tr("configCacheFiltering"));
        filtersGroupStatusIcon.setEnabled(restrictionsActive);
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
        boolean secureLinkVisible = running && isHttpsServerConfigured();
        openSecureServerLink.setVisible(secureLinkVisible);
        openSecureServerLink.setManaged(secureLinkVisible);
    }

    private boolean isHttpsServerConfigured() {
        return httpsServerPort.getText() != null && !httpsServerPort.getText().trim().isBlank();
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
        filterLockStateSwitch.selectedProperty().addListener((_, _, restrictionsActiveRequested) ->
                handleFilterLockStateSwitchChanged(restrictionsActiveRequested));
    }

    private void handleFilterLockStateSwitchChanged(boolean restrictionsActiveRequested) {
        if (syncingConfigurationToForm || syncingFilterLockStateSwitch) {
            return;
        }
        FilterLockService filterLockService = FilterLockService.getInstance();
        if (filterLockService.hasPasswordConfigured() && !filterLockService.isUnlocked()) {
            if (!FilterLockDialogs.ensureUnlocked(this, FILTER_LOCK_UNLOCK_MANAGE_FILTERS_REASON)) {
                syncFilterLockStateSwitch(!persistedPauseFilteringValue);
                return;
            }
            refreshFilterLockUi();
            syncFilterLockStateSwitch(restrictionsActiveRequested);
        }
        saveCurrentSettings(true);
        if (restrictionsActiveRequested) {
            filterLockService.clearUnlockSession();
        }
        refreshFilterLockUi();
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

    private VBox createPlayerChoicesPanel() {
        Label hintLabel = new Label(I18n.tr("configAddPlayerPathsHint"));
        hintLabel.getStyleClass().add("settings-player-panel-hint");
        hintLabel.setWrapText(true);
        hintLabel.setMinWidth(0);
        hintLabel.setMaxWidth(Double.MAX_VALUE);
        UiRenderQuality.optimizeTextNode(hintLabel);

        VBox cardList = new VBox(
                9,
                new ExternalPlayerPathCard(
                        I18n.tr("autoPlayer1"),
                        null,
                        defaultPlayer1,
                        playerPath1,
                        browserButtonPlayerPath1
                ),
                new ExternalPlayerPathCard(
                        I18n.tr("autoPlayer2"),
                        null,
                        defaultPlayer2,
                        playerPath2,
                        browserButtonPlayerPath2
                ),
                new ExternalPlayerPathCard(
                        I18n.tr("autoPlayer3"),
                        null,
                        defaultPlayer3,
                        playerPath3,
                        browserButtonPlayerPath3
                ),
                createEmbeddedPlayerCard(),
                new PlayerOptionCard(
                        I18n.tr("configDefaultWebBrowserPlayer"),
                        null,
                        defaultWebBrowserPlayer,
                        null
                )
        );
        cardList.getStyleClass().add("settings-player-card-list");
        cardList.setMinWidth(0);
        cardList.setMaxWidth(Double.MAX_VALUE);
        UiRenderQuality.optimizeLayout(cardList);

        VBox panel = new VBox(10, hintLabel, cardList);
        panel.getStyleClass().add("settings-player-panel");
        panel.setMinWidth(0);
        panel.setMaxWidth(Double.MAX_VALUE);
        UiRenderQuality.optimizeLayout(panel);
        return panel;
    }

    private PlayerOptionCard createEmbeddedPlayerCard() {
        if (!vlcOptionsLink.getStyleClass().contains("settings-player-link")) {
            vlcOptionsLink.getStyleClass().add("settings-player-link");
        }
        embeddedPlayerCard = new PlayerOptionCard(
                resolveEmbeddedPlayerTitle(),
                null,
                defaultEmbedPlayer,
                null,
                vlcOptionsLink
        );
        return embeddedPlayerCard;
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

    private void clearConfigurationState() {
        // Clear any cached UI state
        settingsSections.clear();
        settingsCardPane.getChildren().clear();
        // Timelines are already stopped by sceneProperty listeners
        statusTitleTimeline = null;
        serverStatusTimeline = null;
    }

    private void refreshConfigurationForm() {
        if (getScene() == null) {
            return;
        }
        refreshFromCurrentConfiguration();
    }

    public void refreshFromCurrentConfiguration() {
        Configuration configuration = service.read();
        runWithAutoSaveSuppressed(() -> {
            applyConfigurationToForm(configuration);
            selectDefaultPlayer(configuration);
            updateVlcOptionsLinkVisibility();
            updateWideViewVisibility();
            refreshServerStatusUI();
            refreshFilterLockUi();
            refreshConfigurationBlockTitles();
        });
        refreshHeaderActions();
    }

    private void refreshFilterLockUi() {
        runWithAutoSaveSuppressed(this::refreshFilterLockUiWithoutAutoSaveGuard);
    }

    private void refreshFilterLockUiWithoutAutoSaveGuard() {
        refreshConfigurationBlockTitles();
        refreshHeaderActions();
        FilterLockService filterLockService = FilterLockService.getInstance();
        boolean passwordSet = filterLockService.hasPasswordConfigured();
        boolean unlocked = filterLockService.isUnlocked();

        filterLockPasswordButton.setText(I18n.tr(passwordSet ? "filterLockChangePasswordAction" : "filterLockSetPasswordAction"));
        if (filterLockStateRow != null) {
            filterLockStateRow.setManaged(true);
            filterLockStateRow.setVisible(true);
        }
        filterLockStateSwitch.setDisable(false);
        syncFilterLockStateSwitch(!persistedPauseFilteringValue);
        updateFilterLockStateValue(!persistedPauseFilteringValue);
        if (filterPasswordProtectionRow != null) {
            filterPasswordProtectionRow.setManaged(passwordSet);
            filterPasswordProtectionRow.setVisible(passwordSet);
        }
        setFilterPasswordProtectionSelection(false);

        if (!passwordSet) {
            filterLockStatusLabel.setText(I18n.tr("filterLockStatusNotSet"));
            filterCategoriesWithTextContains.setEditable(true);
            filterChannelWithTextContains.setEditable(true);
            filterCategoriesWithTextContains.setPromptText(I18n.tr(CONFIG_FILTER_CATEGORIES_PROMPT));
            filterChannelWithTextContains.setPromptText(I18n.tr(CONFIG_FILTER_CHANNELS_PROMPT));
            filterCategoriesWithTextContains.setText(persistedFilterCategoriesValue);
            filterChannelWithTextContains.setText(persistedFilterChannelsValue);
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
            updateFilterLockDurationRowVisibility(true);
            return;
        }

        filterLockStatusLabel.setText(I18n.tr("filterLockStatusLocked", filterLockService.getUnlockWindowMinutes()));
        filterCategoriesWithTextContains.clear();
        filterChannelWithTextContains.clear();
        filterCategoriesWithTextContains.setEditable(false);
        filterChannelWithTextContains.setEditable(false);
        filterCategoriesWithTextContains.setPromptText(I18n.tr("filterLockHiddenCategoriesPrompt"));
        filterChannelWithTextContains.setPromptText(I18n.tr("filterLockHiddenChannelsPrompt"));
        updateFilterLockDurationRowVisibility(false);
    }

    private void updateFilterLockStateValue(boolean restrictionsActive) {
        filterLockStateValueLabel.getStyleClass().removeAll("enabled", "disabled");
        filterLockStateValueLabel.setText(I18n.tr(restrictionsActive ? "commonEnabled" : "commonDisabled"));
        filterLockStateValueLabel.getStyleClass().add(restrictionsActive ? "enabled" : "disabled");
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
        syncingConfigurationToForm = true;
        try {
            this.dbId = configuration.getDbId();
            playerPath1.setText(configuration.getPlayerPath1());
            playerPath2.setText(configuration.getPlayerPath2());
            playerPath3.setText(configuration.getPlayerPath3());
            persistedFilterCategoriesValue = configuration.getFilterCategoriesList() == null ? "" : configuration.getFilterCategoriesList();
            persistedFilterChannelsValue = configuration.getFilterChannelsList() == null ? "" : configuration.getFilterChannelsList();
            persistedPauseFilteringValue = configuration.isPauseFiltering();
            filterCategoriesWithTextContains.setText(persistedFilterCategoriesValue);
            filterChannelWithTextContains.setText(persistedFilterChannelsValue);
            syncFilterLockStateSwitch(!persistedPauseFilteringValue);
            darkThemeCheckBox.setSelected(configuration.isDarkTheme());
            syncThemeModeSelector();
            enableThumbnailsCheckBox.setSelected(configuration.isEnableThumbnails());
            syncThumbnailModeSelector();
            wideViewCheckBox.setSelected(configuration.isWideView());
            serverPort.setText(configuration.getServerPort());
            httpsServerPort.setText(configuration.isHttpsServerEnabled()
                    ? defaultHttpsServerPort(configuration.getHttpsServerPort())
                    : "");
            autoRunServerOnStartupSwitch.setSelected(configuration.isAutoRunServerOnStartup());
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
            vlcNoVideoTitleShow = configuration.isVlcNoVideoTitleShow();
            vlcQuiet = configuration.isVlcQuiet();
            vlcHttpReconnect = configuration.isVlcHttpReconnect();
            vlcAdaptiveUseAccess = configuration.isVlcAdaptiveUseAccess();
            vlcVoutEnabled = configuration.getVlcVout() != null && !configuration.getVlcVout().isBlank();
            vlcAvcodecHwEnabled = configuration.getVlcAvcodecHw() != null && !configuration.getVlcAvcodecHw().isBlank();
            languageComboBox.getSelectionModel().select(I18n.resolveSupportedLanguage(configuration.getLanguageLocale()));
            themeZoomComboBox.getSelectionModel().select(Integer.valueOf(service.normalizeUiZoomPercent(configuration.getUiZoomPercent())));
        } finally {
            syncingConfigurationToForm = false;
        }
    }

    private String defaultHttpsServerPort(String configuredPort) {
        return configuredPort == null || configuredPort.isBlank()
                ? ServerUrlUtil.DEFAULT_HTTPS_SERVER_PORT
                : configuredPort.trim();
    }

    private void saveCurrentSettings(boolean showRestartMessage) {
        if (syncingConfigurationToForm || savingConfiguration) {
            return;
        }
        try {
            savingConfiguration = true;

            Configuration previous = service.read();
            boolean wasAlreadyUnlocked = wasFilterAlreadyUnlocked();
            if (!ensureFilterAccessForPendingSave()) {
                return;
            }
            Configuration newConfiguration = buildConfigurationToSave(previous);
            if (Objects.equals(previous, newConfiguration)) {
                updatePersistedFilterState(newConfiguration);
                refreshConfigurationBlockTitles();
                refreshHeaderActions();
                if (!wasAlreadyUnlocked) {
                    FilterLockService.getInstance().clearUnlockSession();
                }
                return;
            }
            saveConfiguration(newConfiguration);
            applyPostSaveEffects(previous, newConfiguration);
            updatePersistedFilterState(newConfiguration);
            refreshConfigurationBlockTitles();
            refreshHeaderActions();

            if (!wasAlreadyUnlocked) {
                FilterLockService.getInstance().clearUnlockSession();
            }

            if (showRestartMessage && restartRequired(previous, newConfiguration)) {
                showMessageAlert(I18n.tr(CONFIG_EMBED_PLAYER_RESTART_NEEDED));
            }
        } catch (Exception e) {
            showErrorAlert(I18n.tr("configFailedToSave"));
        } finally {
            savingConfiguration = false;
        }
    }

    private void refreshHeaderActions() {
        if (pageHeaderActions != null) {
            pageHeaderActions.refreshState();
        }
    }

    private void updatePersistedFilterState(Configuration configuration) {
        persistedFilterCategoriesValue = configuration.getFilterCategoriesList() == null
                ? ""
                : configuration.getFilterCategoriesList();
        persistedFilterChannelsValue = configuration.getFilterChannelsList() == null
                ? ""
                : configuration.getFilterChannelsList();
        persistedPauseFilteringValue = configuration.isPauseFiltering();
    }

    private Configuration buildConfigurationToSave(Configuration previous) {
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
        configuration.setFilterLockHash(previous == null ? null : previous.getFilterLockHash());
        configuration.setPublishedM3uCategoryMode(previous == null ? null : previous.getPublishedM3uCategoryMode());
        Integer saveDuration = filterLockUnlockDurationComboBox.getValue();
        configuration.setFilterLockUnlockDurationMinutes(saveDuration != null ? String.valueOf(saveDuration) : "15");
        configuration.setUiZoomPercent(String.valueOf(getSelectedThemeZoomPercent()));
        configuration.setAutoRunServerOnStartup(autoRunServerOnStartupSwitch.isSelected());
        configuration.setHttpsServerEnabled(isHttpsServerConfigured());
        configuration.setHttpsServerPort(httpsServerPort.getText() == null ? "" : httpsServerPort.getText().trim());
        configuration.setResolveChainAndDeepRedirects(resolveChainAndDeepRedirectsCheckBox.isSelected());
        configuration.setVlcNetworkCachingMs(vlcNetworkCachingMs);
        configuration.setVlcLiveCachingMs(vlcLiveCachingMs);
        configuration.setEnableVlcHttpUserAgent(vlcHttpUserAgentEnabled);
        configuration.setEnableVlcHttpForwardCookies(vlcHttpForwardCookiesEnabled);
        configuration.setVlcNoVideoTitleShow(vlcNoVideoTitleShow);
        configuration.setVlcQuiet(vlcQuiet);
        configuration.setVlcHttpReconnect(vlcHttpReconnect);
        configuration.setVlcAdaptiveUseAccess(vlcAdaptiveUseAccess);
        configuration.setVlcVout(vlcVoutEnabled ? "true" : null);
        configuration.setVlcAvcodecHw(vlcAvcodecHwEnabled ? "true" : null);
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
        boolean filterTextFieldsEditable = filterCategoriesWithTextContains.isEditable()
                || filterChannelWithTextContains.isEditable();
        boolean filterValuesChanged = (filterTextFieldsEditable
                && (!java.util.Objects.equals(filterCategoriesWithTextContains.getText(), persistedFilterCategoriesValue)
                || !java.util.Objects.equals(filterChannelWithTextContains.getText(), persistedFilterChannelsValue)))
                || isParentalLockRestrictionsPaused() != persistedPauseFilteringValue;
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
        return isParentalLockRestrictionsPaused();
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
            if (newConfiguration.isEnableThumbnails()) {
                ImageCacheManager.clearTransientFailures();
            }
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
        return previousEmbeddedPlayer != current.isEmbeddedPlayer()
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
        if (wideViewRow != null) {
            wideViewRow.setVisible(isEmbedded);
            wideViewRow.setManaged(isEmbedded);
        }
        if (!isEmbedded) {
            wideViewCheckBox.setSelected(false);
        }
    }

    private void openVlcOptionsPopup() {
        Stage activeStage = activeVlcOptionsPopupStage.get();
        if (activeStage != null && activeStage.isShowing()) {
            activeStage.toFront();
            activeStage.requestFocus();
            return;
        }
        Stage popupStage = createPopupStage(I18n.tr("configVlcPopupTitle"));

        ComboBox<VlcCachingOption> networkCachingComboBox = createVlcCachingComboBox();
        ComboBox<VlcCachingOption> liveCachingComboBox = createVlcCachingComboBox();
        SwitchToggle userAgentSwitch = new SwitchToggle();
        SwitchToggle forwardCookiesSwitch = new SwitchToggle();
        SwitchToggle noVideoTitleShowSwitch = new SwitchToggle();
        SwitchToggle quietSwitch = new SwitchToggle();
        SwitchToggle httpReconnectSwitch = new SwitchToggle();
        SwitchToggle adaptiveUseAccessSwitch = new SwitchToggle();
        SwitchToggle voutSwitch = new SwitchToggle();
        SwitchToggle avcodecHwSwitch = new SwitchToggle();

        Runnable loadCurrentValues = () -> {
            networkCachingComboBox.getSelectionModel().select(VlcCachingOption.fromValue(vlcNetworkCachingMs));
            liveCachingComboBox.getSelectionModel().select(VlcCachingOption.fromValue(vlcLiveCachingMs));
            userAgentSwitch.setSelected(vlcHttpUserAgentEnabled);
            forwardCookiesSwitch.setSelected(vlcHttpForwardCookiesEnabled);
            noVideoTitleShowSwitch.setSelected(vlcNoVideoTitleShow);
            quietSwitch.setSelected(vlcQuiet);
            httpReconnectSwitch.setSelected(vlcHttpReconnect);
            adaptiveUseAccessSwitch.setSelected(vlcAdaptiveUseAccess);
            voutSwitch.setSelected(vlcVoutEnabled);
            avcodecHwSwitch.setSelected(vlcAvcodecHwEnabled);
        };
        loadCurrentValues.run();

        Label title = new Label(I18n.tr("configVlcPopupTitle"));
        title.getStyleClass().add("uiptv-vlc-dialog-title");
        UiRenderQuality.optimizeTextNode(title);

        Label description = new Label(I18n.tr("configVlcPopupDescription"));
        description.getStyleClass().add("uiptv-vlc-dialog-description");
        description.setWrapText(true);
        description.setMinWidth(0);
        description.setMaxWidth(Double.MAX_VALUE);
        UiRenderQuality.optimizeTextNode(description);

        VBox optionPanel = new VBox(
                10,
                createVlcComboOptionRow("configVlcNetworkCaching", networkCachingComboBox),
                createVlcComboOptionRow("configVlcLiveCaching", liveCachingComboBox),
                createVlcSwitchOptionRow("configVlcEnableUserAgent", userAgentSwitch),
                createVlcSwitchOptionRow("configVlcForwardCookies", forwardCookiesSwitch),
                createVlcSwitchOptionRow("configVlcNoVideoTitleShow", noVideoTitleShowSwitch),
                createVlcSwitchOptionRow("configVlcQuiet", quietSwitch),
                createVlcSwitchOptionRow("configVlcHttpReconnect", httpReconnectSwitch),
                createVlcSwitchOptionRow("configVlcAdaptiveUseAccess", adaptiveUseAccessSwitch),
                createVlcSwitchOptionRow("configVlcVout", voutSwitch),
                createVlcSwitchOptionRow("configVlcAvcodecHw", avcodecHwSwitch)
        );
        optionPanel.getStyleClass().add("uiptv-vlc-option-panel");
        optionPanel.setMinWidth(0);
        optionPanel.setMaxWidth(Double.MAX_VALUE);
        UiRenderQuality.optimizeLayout(optionPanel);

        VBox root = new VBox(14, title, description, optionPanel);
        root.getStyleClass().add("uiptv-vlc-dialog-content");
        root.setPadding(new Insets(15));
        root.setMinWidth(0);
        root.setMaxWidth(Double.MAX_VALUE);
        UiRenderQuality.optimizeLayout(root);

        Runnable closeAction = popupStage::close;

        Button saveButton = new Button(I18n.tr("commonSave"));
        saveButton.getStyleClass().add("uiptv-inline-primary-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> {
            vlcNetworkCachingMs = selectedCachingValue(networkCachingComboBox);
            vlcLiveCachingMs = selectedCachingValue(liveCachingComboBox);
            vlcHttpUserAgentEnabled = userAgentSwitch.isSelected();
            vlcHttpForwardCookiesEnabled = forwardCookiesSwitch.isSelected();
            vlcNoVideoTitleShow = noVideoTitleShowSwitch.isSelected();
            vlcQuiet = quietSwitch.isSelected();
            vlcHttpReconnect = httpReconnectSwitch.isSelected();
            vlcAdaptiveUseAccess = adaptiveUseAccessSwitch.isSelected();
            vlcVoutEnabled = voutSwitch.isSelected();
            vlcAvcodecHwEnabled = avcodecHwSwitch.isSelected();
            saveVlcOptionsConfiguration(true);
            closeAction.run();
        });

        Button resetButton = new Button(I18n.tr("configVlcResetDefaults"));
        resetButton.getStyleClass().add("uiptv-inline-secondary-button");
        resetButton.setOnAction(event -> {
            vlcNetworkCachingMs = ConfigurationService.DEFAULT_VLC_CACHING_MS;
            vlcLiveCachingMs = ConfigurationService.DEFAULT_VLC_CACHING_MS;
            vlcHttpUserAgentEnabled = true;
            vlcHttpForwardCookiesEnabled = true;
            vlcNoVideoTitleShow = true;
            vlcQuiet = true;
            vlcHttpReconnect = true;
            vlcAdaptiveUseAccess = true;
            vlcVoutEnabled = true;
            vlcAvcodecHwEnabled = true;
            saveVlcOptionsConfiguration(true);
            closeAction.run();
        });

        Button closeButton = new Button(I18n.tr("commonClose"));
        closeButton.getStyleClass().add("uiptv-inline-secondary-button");
        closeButton.setOnAction(event -> closeAction.run());

        HBox buttons = new HBox(10, closeButton, resetButton, saveButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.getStyleClass().add("management-popup-footer");
        root.getChildren().add(buttons);

        popupStage.setScene(createPopupScene(root, 940, 860));
        popupStage.setOnHidden(e -> activeVlcOptionsPopupStage.compareAndSet(popupStage, null));
        activeVlcOptionsPopupStage.set(popupStage);
        popupStage.showAndWait();
    }

    private Node createVlcComboOptionRow(String labelKey, ComboBox<VlcCachingOption> comboBox) {
        Label label = createVlcOptionLabel(labelKey);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox row = new HBox(12, label, comboBox);
        row.getStyleClass().add("uiptv-vlc-option-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private Node createVlcSwitchOptionRow(String labelKey, SwitchToggle switchToggle) {
        Label label = createVlcOptionLabel(labelKey);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, label, spacer, switchToggle);
        row.getStyleClass().add("uiptv-vlc-option-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private Label createVlcOptionLabel(String labelKey) {
        Label label = new Label(I18n.tr(labelKey));
        label.getStyleClass().add("uiptv-vlc-option-label");
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setPrefWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        UiRenderQuality.optimizeTextNode(label);
        return label;
    }

    private ComboBox<VlcCachingOption> createVlcCachingComboBox() {
        ComboBox<VlcCachingOption> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(VlcCachingOption.all());
        comboBox.getStyleClass().add("uiptv-vlc-combo-box");
        comboBox.setMinWidth(0);
        comboBox.setPrefWidth(325);
        comboBox.setMaxWidth(350);
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
        importDatabaseButton.getStyleClass().add("settings-inline-action");
        exportDatabaseButton.getStyleClass().add("settings-inline-action");
        Label description = new Label(I18n.tr("configDatabaseSyncDescription"));
        description.getStyleClass().add("settings-section-description");
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);

        Node createBackupCard = createDatabaseSyncActionCard(
                false,
                CONFIG_EXPORT_DATABASE,
                "configExportDatabasePopupDescription",
                BACKUP_CREATE_ICON_PATH,
                exportDatabaseButton,
                true
        );
        Node restoreBackupCard = createDatabaseSyncActionCard(
                true,
                CONFIG_IMPORT_DATABASE,
                "configImportDatabasePopupDescription",
                BACKUP_RESTORE_ICON_PATH,
                importDatabaseButton,
                false
        );
        return new VBox(10, description, createBackupCard, restoreBackupCard);
    }

    private Node createDatabaseSyncActionCard(boolean importMode,
                                              String titleKey,
                                              String descriptionKey,
                                              String iconPath,
                                              Hyperlink actionLink,
                                              boolean prominent) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("settings-backup-action-icon");
        icon.setScaleX(0.78);
        icon.setScaleY(0.78);

        StackPane iconBadge = new StackPane(icon);
        iconBadge.getStyleClass().add("settings-backup-action-badge");

        Label title = new Label(I18n.tr(titleKey));
        title.getStyleClass().add("settings-backup-action-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);

        Label description = new Label(I18n.tr(descriptionKey));
        description.getStyleClass().add("settings-backup-action-description");
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);

        actionLink.setText(I18n.tr(titleKey));
        actionLink.setFocusTraversable(true);
        actionLink.setMaxWidth(Region.USE_PREF_SIZE);

        VBox textColumn = new VBox(3, title, description, actionLink);
        textColumn.setMinWidth(0);
        textColumn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textColumn, Priority.ALWAYS);

        HBox card = new HBox(10, iconBadge, textColumn);
        card.getStyleClass().add("settings-backup-action-card");
        if (prominent) {
            card.getStyleClass().add("primary");
        }
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setFocusTraversable(true);
        card.setOnMouseClicked(event -> {
            if (event.getTarget() instanceof Node node && isNodeInside(node, actionLink)) {
                return;
            }
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) {
                return;
            }
            openDatabaseSyncPopup(importMode);
            event.consume();
        });
        card.setOnKeyPressed(event -> {
            if (event.getTarget() instanceof Node node && isNodeInside(node, actionLink)) {
                return;
            }
            switch (event.getCode()) {
                case ENTER, SPACE -> {
                    openDatabaseSyncPopup(importMode);
                    event.consume();
                }
                default -> {
                }
            }
        });
        return card;
    }

    private boolean isNodeInside(Node node, Node possibleAncestor) {
        Node current = node;
        while (current != null) {
            if (current == possibleAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void saveVlcOptionsConfiguration(boolean showReloadMessage) {
        try {
            savingConfiguration = true;
            Configuration previous = service.read();
            Configuration configuration = service.read();
            configuration.setVlcNetworkCachingMs(vlcNetworkCachingMs);
            configuration.setVlcLiveCachingMs(vlcLiveCachingMs);
            configuration.setEnableVlcHttpUserAgent(vlcHttpUserAgentEnabled);
            configuration.setEnableVlcHttpForwardCookies(vlcHttpForwardCookiesEnabled);
            configuration.setVlcNoVideoTitleShow(vlcNoVideoTitleShow);
            configuration.setVlcQuiet(vlcQuiet);
            configuration.setVlcHttpReconnect(vlcHttpReconnect);
            configuration.setVlcAdaptiveUseAccess(vlcAdaptiveUseAccess);
            configuration.setVlcVout(vlcVoutEnabled ? "true" : null);
            configuration.setVlcAvcodecHw(vlcAvcodecHwEnabled ? "true" : null);
            service.save(configuration);
            applyPostSaveEffects(previous, configuration);
            if (onSaveCallback != null) {
                onSaveCallback.call(null);
            }
            if (showReloadMessage && vlcSettingsChanged(previous, configuration)) {
                showMessageAlert(I18n.tr(CONFIG_EMBED_PLAYER_RESTART_NEEDED));
            }
        } finally {
            savingConfiguration = false;
        }
    }

    private void openDatabaseSyncPopup(boolean importMode) {
        Stage activeStage = activeDatabaseSyncPopupStage.get();
        if (activeStage != null && activeStage.isShowing()) {
            activeStage.toFront();
            activeStage.requestFocus();
            return;
        }
        Stage popupStage = createPopupStage(I18n.tr(importMode ? CONFIG_IMPORT_DATABASE : CONFIG_EXPORT_DATABASE));
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
        Runnable closeAction = () -> {
            if (syncRunning.get()) {
                return;
            }
            popupStage.close();
        };
        popupStage.setOnCloseRequest(event -> {
            if (syncRunning.get()) {
                event.consume();
            }
        });

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
                importMode,
                fileModeButton.isSelected(),
                databasePathField.getText(),
                remoteHostField.getText(),
                remotePortField.getText(),
                syncConfigurationCheckBox.isSelected(),
                syncExternalPlayerPathsCheckBox.isSelected(),
                controls
        )));
        cancelButton.setOnAction(event -> closeAction.run());

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
        Label directionTitleLabel = new Label();
        directionTitleLabel.getStyleClass().add("management-popup-section-title");
        directionTitleLabel.setWrapText(true);
        Label descriptionLabel = new Label();
        descriptionLabel.setWrapText(true);

        Runnable textUpdater = () -> {
            updateDatabaseSyncDirectionalText(
                    directionTitleLabel,
                    descriptionLabel,
                    runButton,
                    importMode,
                    fileModeButton.isSelected()
            );
            popupStage.setTitle(directionTitleLabel.getText());
        };
        fileModeButton.selectedProperty().addListener((obs, oldValue, newValue) -> {
            textUpdater.run();
        });
        remoteModeButton.selectedProperty().addListener((obs, oldValue, newValue) -> {
            textUpdater.run();
        });
        textUpdater.run();

        VBox root = new VBox(
                12,
                directionTitleLabel,
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
        root.getStyleClass().addAll("management-popup-root", "database-sync-inline");
        root.setPadding(new Insets(14));
        root.setPrefWidth(DATABASE_SYNC_INLINE_WIDTH);
        root.setMaxWidth(Double.MAX_VALUE);

        popupStage.setScene(createPopupScene(root, DATABASE_SYNC_INLINE_WIDTH, 560));
        popupStage.setOnHidden(e -> activeDatabaseSyncPopupStage.compareAndSet(popupStage, null));
        activeDatabaseSyncPopupStage.set(popupStage);
        popupStage.showAndWait();
    }

    private void updateDatabaseSyncDirectionalText(Label directionTitleLabel,
                                                   Label descriptionLabel,
                                                   Button runButton,
                                                   boolean importMode,
                                                   boolean fileMode) {
        String sourceLabel = databaseSyncSourceLabel(importMode, fileMode);
        String destinationLabel = databaseSyncDestinationLabel(importMode, fileMode);
        directionTitleLabel.setText(I18n.tr("configDatabaseSyncDirectionTitle", sourceLabel, destinationLabel));
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
        });

        Thread worker = new Thread(task, request.importMode() ? "database-restore-task" : "database-backup-task");
        worker.setDaemon(true);
        worker.start();
    }

    private void runRemoteDatabaseSyncAction(boolean importMode,
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

    private record DatabaseSyncRunRequest(boolean importMode,
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
        Label helpText = new Label(text);
        helpText.getStyleClass().add("uiptv-dialog-description");
        helpText.setWrapText(true);
        helpText.setMinWidth(0);
        helpText.setMaxWidth(Double.MAX_VALUE);

        ButtonType closeButton = new ButtonType(I18n.tr("commonClose"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ThemedDialogSupport.showChoice(title, helpText, List.of(closeButton), closeButton);
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

    private void addBrowserButton1ClickHandler() {
        browserButtonPlayerPath1.setOnAction(_ -> {
            try {
                File file = fileChooser.showOpenDialog(RootApplication.getPrimaryStage());
                if (file != null) {
                    playerPath1.setText(file.getAbsolutePath());
                }
            } finally {
                browserButtonPlayerPath1.setDisable(false);
            }
        });
    }

    private void addBrowserButton2ClickHandler() {
        browserButtonPlayerPath2.setOnAction(_ -> {
            try {
                File file = fileChooser.showOpenDialog(RootApplication.getPrimaryStage());
                if (file != null) {
                    playerPath2.setText(file.getAbsolutePath());
                }
            } finally {
                browserButtonPlayerPath2.setDisable(false);
            }
        });
    }

    private void addBrowserButton3ClickHandler() {
        browserButtonPlayerPath3.setOnAction(_ -> {
            try {
                File file = fileChooser.showOpenDialog(RootApplication.getPrimaryStage());
                if (file != null) {
                    playerPath3.setText(file.getAbsolutePath());
                }
            } finally {
                browserButtonPlayerPath3.setDisable(false);
            }
        });
    }

    private void installPlayerSelectionConfirmationHandler() {
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (ignorePlayerSelectionPrompt || newToggle == null) {
                return;
            }
            if (newToggle == defaultWebBrowserPlayer) {
                confirmPlayerSelection(oldToggle, "configBrowserCompatibilityWarning");
                return;
            }
            if (newToggle == defaultEmbedPlayer) {
                confirmPlayerSelection(oldToggle, "configEmbedPlayerVlcWarning");
            }
        });
    }

    private void confirmPlayerSelection(Toggle oldToggle, String messageKey) {
        playerSelectionConfirmationActive = true;
        boolean proceed = false;
        try {
            proceed = UIptvAlert.showConfirmationAlert(I18n.tr(messageKey));
            if (!proceed) {
                restorePreviousPlayerSelection(oldToggle);
            }
        } finally {
            playerSelectionConfirmationActive = false;
        }

        boolean shouldSave = proceed && playerSelectionSaveDeferred;
        playerSelectionSaveDeferred = false;
        if (shouldSave) {
            Platform.runLater(() -> {
                updateVlcOptionsLinkVisibility();
                updateWideViewVisibility();
                requestImmediateAutoSave("playerSelectionDeferred");
            });
        }
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
