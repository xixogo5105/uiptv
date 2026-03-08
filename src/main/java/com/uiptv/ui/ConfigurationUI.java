package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.Configuration;
import com.uiptv.model.ThemeCssOverride;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.server.UIptvServer;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.ThemeCssOverrideService;
import com.uiptv.util.I18n;
import com.uiptv.util.ThemeStylesheetResolver;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.util.StyleClassDecorator;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.UIptvAlert;
import com.uiptv.widget.UIptvText;
import com.uiptv.widget.UIptvTextArea;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ConfigurationUI extends VBox {
    private static final String EMBEDDED_PLAYER_PATH = PlaybackUIService.EMBEDDED_PLAYER_PATH;
    private static final String TMDB_API_GUIDE_URL = "https://developer.themoviedb.org/docs/getting-started";
    private static final String TMDB_API_KEY_URL = "https://www.themoviedb.org/settings/api";
    private static final String CONFIG_DEFAULT_RESOURCE_IN_USE = "configDefaultResourceInUse";
    private static final String STYLE_CLASS_DANGEROUS = "dangerous";
    private static final String STYLE_CLASS_DIM_LABEL = "dim-label";
    private static final String STYLE_CLASS_OUTLINE_PANE = "uiptv-outline-pane";
    private String dbId;
    private final VBox contentContainer = new VBox();
    final ToggleGroup group = new ToggleGroup();
    final Button browserButtonPlayerPath1 = new Button("...");
    final Button browserButtonPlayerPath2 = new Button("...");
    final Button browserButtonPlayerPath3 = new Button("...");
    final FileChooser fileChooser = new FileChooser();
    private final RadioButton defaultPlayer1 = new RadioButton("");
    private final RadioButton defaultPlayer2 = new RadioButton("");
    private final RadioButton defaultPlayer3 = new RadioButton("");
    private final RadioButton defaultEmbedPlayer = new RadioButton();
    private final RadioButton defaultWebBrowserPlayer = new RadioButton(I18n.tr("configDefaultWebBrowserPlayer"));
    private boolean ignorePlayerSelectionPrompt = false;

    private final UIptvText playerPath1 = new UIptvText("playerPath1", "configPlayerPath1Prompt", 5);
    private final UIptvText playerPath2 = new UIptvText("playerPath2", "configPlayerPath2Prompt", 5);
    private final UIptvText playerPath3 = new UIptvText("playerPath3", "configPlayerPath3Prompt", 5);
    private final UIptvTextArea filterCategoriesWithTextContains = new UIptvTextArea("filterCategoriesWithTextContains", "configFilterCategoriesPrompt", 5);
    private final UIptvTextArea filterChannelWithTextContains = new UIptvTextArea("filterChannelWithTextContains", "configFilterChannelsPrompt", 5);
    private final CheckBox filterPausedCheckBox = new CheckBox(I18n.tr("configPauseFiltering"));
    private final CheckBox darkThemeCheckBox = new CheckBox(I18n.tr("configUseDarkTheme"));
    private final CheckBox enableFfmpegCheckBox = new CheckBox(I18n.tr("configEnableFfmpeg"));
    private final CheckBox enableLitePlayerFfmpegCheckBox = new CheckBox(I18n.tr("configEnableLitePlayerFfmpeg"));
    private final CheckBox enableThumbnailsCheckBox = new CheckBox(I18n.tr("configEnableThumbnails"));
    private final CheckBox wideViewCheckBox = new CheckBox(I18n.tr("configWideView"));
    private final ComboBox<I18n.SupportedLanguage> languageComboBox = new ComboBox<>();
    private final ComboBox<Integer> themeZoomComboBox = new ComboBox<>();
    private final TextField lightThemeCssStatus = new TextField(I18n.tr(CONFIG_DEFAULT_RESOURCE_IN_USE));
    private final TextField darkThemeCssStatus = new TextField(I18n.tr(CONFIG_DEFAULT_RESOURCE_IN_USE));
    private final Button uploadLightThemeCssButton = new Button(I18n.tr("configCssUploadLight"));
    private final Button uploadDarkThemeCssButton = new Button(I18n.tr("configCssUploadDark"));
    private final Hyperlink downloadLightThemeCssLink = new Hyperlink(I18n.tr("configLightCss"));
    private final Hyperlink downloadDarkThemeCssLink = new Hyperlink(I18n.tr("configDarkCss"));
    private final Button resetThemeOverridesButton = new Button(I18n.tr("configResetThemeOverrides"));
    private final FileChooser cssFileChooser = new FileChooser();
    private ThemeCssOverride currentThemeCssOverride = new ThemeCssOverride();
    private final UIptvText serverPort = new UIptvText("serverPort", "configServerPortPrompt", 3);
    private final UIptvText cacheExpiryDays = new UIptvText("cacheExpiryDays", "configCacheExpiryPrompt", 5);
    private final PasswordField tmdbReadAccessToken = new PasswordField();
    private final Hyperlink tmdbApiGuideLink = new Hyperlink(I18n.tr("configTmdbApiGuideLink"));
    private final Hyperlink tmdbApiKeyPageLink = new Hyperlink(I18n.tr("configTmdbApiKeyPageLink"));

    private final Button startServerButton = new Button(I18n.tr("configStartServer"));
    private final Hyperlink openServerLink = new Hyperlink(I18n.tr("configOpenWebApp"));
    private final Button publishM3u8Button = new Button(I18n.tr("configPublishM3u8"));
    private final Button clearCacheButton = new Button(I18n.tr("configClearCache"));
    private final Button clearWatchingNowButton = new Button(I18n.tr("configClearWatchingNow"));
    private final Button reloadCacheButton = new Button(I18n.tr("configReloadAccountsCache"));
    private final ProminentButton saveButton = new ProminentButton(I18n.tr("commonSave"));
    private final Callback onSaveCallback;
    private final ConfigurationService service = ConfigurationService.getInstance();
    private final ThemeCssOverrideService themeCssOverrideService = ThemeCssOverrideService.getInstance();
    private final CacheService cacheService = new CacheServiceImpl();
    private Timeline serverStatusTimeline;
    private Timeline saveSuccessTimeline;

    public ConfigurationUI(Callback onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
        initWidgets();
    }

    private void initWidgets() {
        setPadding(Insets.EMPTY);
        setSpacing(0);
        startServerButton.getStyleClass().add("no-dim-disabled");
        contentContainer.setPadding(new Insets(5));
        contentContainer.setSpacing(10);
        cssFileChooser.setTitle(I18n.tr("configCssSelectFile"));
        cssFileChooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter(I18n.tr("configCssFiles"), "*.css"),
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
        currentThemeCssOverride = themeCssOverrideService.read();
        configurePlayerToggleGroup();
        updateEmbeddedPlayerTitle();
        configurePlayerUserData();
        defaultEmbedPlayer.setSelected(true);
        if (configuration != null) {
            this.dbId = configuration.getDbId();
            playerPath1.setText(configuration.getPlayerPath1());
            playerPath2.setText(configuration.getPlayerPath2());
            playerPath3.setText(configuration.getPlayerPath3());
            filterCategoriesWithTextContains.setText(configuration.getFilterCategoriesList());
            filterChannelWithTextContains.setText(configuration.getFilterChannelsList());
            filterPausedCheckBox.setSelected(configuration.isPauseFiltering());
            darkThemeCheckBox.setSelected(configuration.isDarkTheme());
            enableThumbnailsCheckBox.setSelected(configuration.isEnableThumbnails());
            wideViewCheckBox.setSelected(configuration.isWideView());
            serverPort.setText(configuration.getServerPort());
            enableFfmpegCheckBox.setSelected(configuration.isEnableFfmpegTranscoding());
            enableLitePlayerFfmpegCheckBox.setSelected(configuration.isEnableLitePlayerFfmpeg());
            cacheExpiryDays.setText(String.valueOf(service.normalizeCacheExpiryDays(configuration.getCacheExpiryDays())));
            tmdbReadAccessToken.setText(configuration.getTmdbReadAccessToken());
        }
        selectDefaultPlayer(configuration);
        if (cacheExpiryDays.getText() == null || cacheExpiryDays.getText().isBlank()) {
            cacheExpiryDays.setText(String.valueOf(ConfigurationService.DEFAULT_CACHE_EXPIRY_DAYS));
        }
        cacheExpiryDays.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }
            String normalized = newVal.replaceAll("[^0-9]", "");
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

        filterPausedCheckBox.setMinWidth(250);
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
        HBox box4 = new HBox(6, defaultEmbedPlayer, wideViewCheckBox);
        HBox box5 = new HBox(6, defaultWebBrowserPlayer);
        Label tmdbTokenLabel = new Label(I18n.tr("configTmdbReadAccessToken"));
        Label tmdbHelpLabel = new Label(I18n.tr("configTmdbReadAccessTokenHelp"));
        tmdbHelpLabel.setWrapText(true);
        tmdbHelpLabel.getStyleClass().add(STYLE_CLASS_DIM_LABEL);
        HBox tmdbLinksRow = new HBox(10, tmdbApiGuideLink, tmdbApiKeyPageLink);
        VBox tmdbConfigSection = new VBox(6, tmdbTokenLabel, tmdbReadAccessToken, tmdbHelpLabel, tmdbLinksRow);
        tmdbConfigSection.getStyleClass().add(STYLE_CLASS_OUTLINE_PANE);
        VBox playersGroup = new VBox(10, box1, box2, box3, box4, box5);

        VBox filtersGroup = new VBox(10, filterCategoriesWithTextContains, filterChannelWithTextContains);

        VBox themeOverridesGroup = buildThemeOverrideGroup();
        updateThemeCssStatusLabels();

        HBox clearButtons = new HBox(10, clearCacheButton, clearWatchingNowButton);
        reloadCacheButton.setMaxWidth(Double.MAX_VALUE);
        VBox cacheGroup = new VBox(10, filterPausedCheckBox, cacheExpiryRow, clearButtons, reloadCacheButton);

        openServerLink.setVisible(false);
        openServerLink.setManaged(false);
        HBox serverButtonWrapper = new HBox(10, serverPort, startServerButton, openServerLink);
        publishM3u8Button.setMaxWidth(Double.MAX_VALUE);
        publishM3u8Button.setPrefWidth(440);
        VBox serverGroup = new VBox(10, enableFfmpegCheckBox, enableLitePlayerFfmpegCheckBox, serverButtonWrapper, publishM3u8Button);
        serverGroup.setFillWidth(true);
        configureWrappingCheckBox(enableFfmpegCheckBox, serverGroup);
        configureWrappingCheckBox(enableLitePlayerFfmpegCheckBox, serverGroup);

        contentContainer.getChildren().addAll(
                createCollapsibleGroupPane(I18n.tr("configVideoPlayers"), I18n.tr("configAddPlayerPathsHint"), playersGroup, false),
                createCollapsibleGroupPane(I18n.tr("configFilters"), null, filtersGroup, true),
                createCollapsibleGroupPane(I18n.tr("configDarkTheme"), I18n.tr("configThemeDescription"), themeOverridesGroup, true),
                createCollapsibleGroupPane(I18n.tr("configCacheFiltering"), null, cacheGroup, true),
                createCollapsibleGroupPane(I18n.tr("configFfmpegAndWebServer"), null, serverGroup, true),
                createCollapsibleGroupPane(I18n.tr("configTmdbMetadata"), null, tmdbConfigSection, true),
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
        addThemePreviewHandlers();
        installPlayerSelectionConfirmationHandler();
        installServerStatusMonitor();
    }

    private void configureWrappingCheckBox(CheckBox checkBox, VBox container) {
        checkBox.setWrapText(true);
        checkBox.setAlignment(Pos.TOP_LEFT);
        checkBox.setMaxWidth(Double.MAX_VALUE);
        checkBox.setMinHeight(Region.USE_PREF_SIZE);
        checkBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        checkBox.prefWidthProperty().bind(container.widthProperty().subtract(4));
    }

    private BorderPane createCollapsibleGroupPane(String title, String description, Node content, boolean collapsedByDefault) {
        BorderPane pane = new BorderPane(content);
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

        final Runnable refreshToggleLabel = () -> {
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
        pane.setTop(header);
        pane.setPadding(new Insets(10));
        pane.getStyleClass().add("uiptv-card");
        return pane;
    }
    private VBox buildThemeOverrideGroup() {
        languageComboBox.getStyleClass().add("uiptv-combo-box");
        languageComboBox.setMaxWidth(Double.MAX_VALUE);
        Label languageLabel = new Label(I18n.tr("configLanguage"));
        HBox.setHgrow(languageComboBox, Priority.ALWAYS);

        themeZoomComboBox.getStyleClass().add("uiptv-combo-box");
        themeZoomComboBox.setMaxWidth(Double.MAX_VALUE);
        Label themeZoomLabel = new Label(I18n.tr("configThemeZoom"));
        HBox.setHgrow(themeZoomComboBox, Priority.ALWAYS);

        Label themeZoomHelpLabel = new Label(I18n.tr("configThemeZoomHelp"));
        themeZoomHelpLabel.setWrapText(true);
        themeZoomHelpLabel.getStyleClass().add(STYLE_CLASS_DIM_LABEL);

        lightThemeCssStatus.setEditable(false);
        darkThemeCssStatus.setEditable(false);
        lightThemeCssStatus.setFocusTraversable(false);
        darkThemeCssStatus.setFocusTraversable(false);
        lightThemeCssStatus.setMaxWidth(Double.MAX_VALUE);
        darkThemeCssStatus.setMaxWidth(Double.MAX_VALUE);

        HBox lightUploadRow = new HBox(8, lightThemeCssStatus, uploadLightThemeCssButton);
        HBox.setHgrow(lightThemeCssStatus, Priority.ALWAYS);
        lightUploadRow.setFillHeight(true);
        lightUploadRow.setMaxWidth(Double.MAX_VALUE);
        VBox lightBox = new VBox(6, new Label(I18n.tr("configLightThemeCssOverride")), lightUploadRow);

        HBox darkUploadRow = new HBox(8, darkThemeCssStatus, uploadDarkThemeCssButton);
        HBox.setHgrow(darkThemeCssStatus, Priority.ALWAYS);
        darkUploadRow.setFillHeight(true);
        darkUploadRow.setMaxWidth(Double.MAX_VALUE);
        VBox darkBox = new VBox(6, new Label(I18n.tr("configDarkThemeCssOverride")), darkUploadRow);

        Label downloadLabel = new Label(I18n.tr("configCssDownload"));
        HBox downloadRow = new HBox(8, downloadLabel, downloadLightThemeCssLink, downloadDarkThemeCssLink);
        downloadRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        downloadRow.setMaxWidth(Double.MAX_VALUE);

        VBox themeCssSection = new VBox(10, lightBox, darkBox, downloadRow, resetThemeOverridesButton);
        themeCssSection.getStyleClass().add(STYLE_CLASS_OUTLINE_PANE);
        themeCssSection.setMaxWidth(Double.MAX_VALUE);

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

        VBox languageAndZoomSection = new VBox(10, languageAndZoomGrid, themeZoomHelpLabel);
        languageAndZoomSection.getStyleClass().add(STYLE_CLASS_OUTLINE_PANE);
        languageAndZoomSection.setMaxWidth(Double.MAX_VALUE);

        addThemeCssButtonHandlers();
        return new VBox(10, darkThemeCheckBox, enableThumbnailsCheckBox, themeCssSection, languageAndZoomSection);
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

    private void addThemeCssButtonHandlers() {
        uploadLightThemeCssButton.setOnAction(event -> uploadThemeCss(false));
        uploadDarkThemeCssButton.setOnAction(event -> uploadThemeCss(true));
        downloadLightThemeCssLink.setOnAction(event -> downloadDefaultThemeCss(false));
        downloadDarkThemeCssLink.setOnAction(event -> downloadDefaultThemeCss(true));
        resetThemeOverridesButton.setOnAction(event -> resetThemeOverrides());
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

    private void uploadThemeCss(boolean darkTheme) {
        File file = cssFileChooser.showOpenDialog(RootApplication.primaryStage);
        if (file == null) {
            return;
        }
        try {
            String cssContents = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (darkTheme) {
                currentThemeCssOverride.setDarkThemeCssName(file.getName());
                currentThemeCssOverride.setDarkThemeCssContent(cssContents);
            } else {
                currentThemeCssOverride.setLightThemeCssName(file.getName());
                currentThemeCssOverride.setLightThemeCssContent(cssContents);
            }
            updateThemeCssStatusLabels();
        } catch (Exception e) {
            showErrorAlert(I18n.tr("configUnableToReadCss"));
        }
    }

    private void clearThemeCssOverride(boolean darkTheme) {
        if (darkTheme) {
            currentThemeCssOverride.setDarkThemeCssName(null);
            currentThemeCssOverride.setDarkThemeCssContent(null);
        } else {
            currentThemeCssOverride.setLightThemeCssName(null);
            currentThemeCssOverride.setLightThemeCssContent(null);
        }
        updateThemeCssStatusLabels();
    }

    private void resetThemeOverrides() {
        clearThemeCssOverride(false);
        clearThemeCssOverride(true);
    }

    private void downloadDefaultThemeCss(boolean darkTheme) {
        try {
            String cssContent = ThemeStylesheetResolver.readDefaultStylesheetContent(getClass(), darkTheme);
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.tr("configCssTemplateSave"));
            chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(I18n.tr("configCssFiles"), "*.css"));
            chooser.setInitialFileName(darkTheme ? "dark-application.css" : "application.css");
            File target = chooser.showSaveDialog(RootApplication.primaryStage);
            if (target == null) {
                return;
            }
            Files.writeString(target.toPath(), cssContent, StandardCharsets.UTF_8);
            showMessageAlert(I18n.tr("configCssExportSuccess"));
        } catch (Exception e) {
            showErrorAlert(I18n.tr("configCssExportFailed"));
        }
    }

    private void updateThemeCssStatusLabels() {
        String lightName = currentThemeCssOverride.getLightThemeCssName();
        String darkName = currentThemeCssOverride.getDarkThemeCssName();
        lightThemeCssStatus.setText(lightName == null || lightName.isBlank()
                ? I18n.tr(CONFIG_DEFAULT_RESOURCE_IN_USE)
                : I18n.tr("configUsingOverridePrefix") + " " + lightName);
        darkThemeCssStatus.setText(darkName == null || darkName.isBlank()
                ? I18n.tr(CONFIG_DEFAULT_RESOURCE_IN_USE)
                : I18n.tr("configUsingOverridePrefix") + " " + darkName);
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
        } else if (playerType == VideoPlayerInterface.PlayerType.LITE) {
            title = I18n.tr("configEmbeddedPlayerLite");
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
                    SeriesWatchStateService.getInstance().clearAllSeriesLastWatched();
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
                if (UIptvServer.isRunning()) {
                    UIptvServer.stop();
                } else {
                    UIptvServer.start();
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
            Stage popupStage = new Stage();
            M3U8PublicationPopup popup = new M3U8PublicationPopup(popupStage);
            Scene scene = new Scene(popup, 400, 300);
            I18n.applySceneOrientation(scene);
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
            popupStage.setTitle(I18n.tr("configPublishM3u8"));
            popupStage.setScene(scene);
            popupStage.showAndWait();
        });
    }

    private void installServerStatusMonitor() {
        refreshServerStatusUI();
        serverStatusTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshServerStatusUI()));
        serverStatusTimeline.setCycleCount(Animation.INDEFINITE);
        serverStatusTimeline.play();

        sceneProperty().addListener((observable, oldScene, newScene) -> {
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

    private void refreshServerStatusUI() {
        boolean running = UIptvServer.isRunning();
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
    }

    private void configurePlayerToggleGroup() {
        defaultPlayer1.setToggleGroup(group);
        defaultPlayer2.setToggleGroup(group);
        defaultPlayer3.setToggleGroup(group);
        defaultEmbedPlayer.setToggleGroup(group);
        defaultWebBrowserPlayer.setToggleGroup(group);
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
        openServerLink.setOnAction(event -> {
            ServerUrlUtil.openInBrowser(ServerUrlUtil.getLocalServerUrl() + "/");
        });
    }

    private void addTmdbGuideLinkClickHandler() {
        tmdbApiGuideLink.setOnAction(event -> ServerUrlUtil.openInBrowser(TMDB_API_GUIDE_URL));
        tmdbApiKeyPageLink.setOnAction(event -> ServerUrlUtil.openInBrowser(TMDB_API_KEY_URL));
    }

    private String resolveServerPort() {
        String port = serverPort.getText();
        if (port == null || port.isBlank()) {
            Configuration configuration = service.read();
            if (configuration != null) {
                port = configuration.getServerPort();
            }
        }
        return (port == null || port.isBlank()) ? "8888" : port.trim();
    }

    private void addSaveButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> {
            try {
                if (saveButton.isDisable()) {
                    return;
                }
                saveButton.setDisable(true);

                Configuration previous = service.read();
                Configuration newConfiguration = buildConfigurationToSave();
                saveConfiguration(newConfiguration);
                applyPostSaveEffects(previous, newConfiguration);
                showSaveSuccessAnimation();
                if (restartRequired(previous, newConfiguration)) {
                    showMessageAlert(I18n.tr("configEmbedPlayerRestartNeeded"));
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
                filterCategoriesWithTextContains.getText(), filterChannelWithTextContains.getText(),
                filterPausedCheckBox.isSelected(),
                darkThemeCheckBox.isSelected(), serverPort.getText(),
                defaultEmbedPlayer.isSelected(),
                enableFfmpegCheckBox.isSelected(),
                sanitizeCacheExpiryDaysText(),
                enableThumbnailsCheckBox.isSelected()
        );
        configuration.setDbId(dbId);
        configuration.setWideView(wideViewCheckBox.isSelected());
        configuration.setLanguageLocale(getSelectedLanguageTag());
        configuration.setTmdbReadAccessToken(tmdbReadAccessToken.getText() == null ? "" : tmdbReadAccessToken.getText().trim());
        configuration.setUiZoomPercent(String.valueOf(getSelectedThemeZoomPercent()));
        configuration.setEnableLitePlayerFfmpeg(enableLitePlayerFfmpegCheckBox.isSelected());
        return configuration;
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
        currentThemeCssOverride.setUpdatedAt(String.valueOf(System.currentTimeMillis()));
        themeCssOverrideService.save(currentThemeCssOverride);
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
    }

    private void applyPostSaveEffects(Configuration previous, Configuration newConfiguration) {
        if (thumbnailModeChanged(previous, newConfiguration)) {
            ThumbnailAwareUI.notifyThumbnailModeChanged(newConfiguration.isEnableThumbnails());
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
        browserButtonPlayerPath1.setOnAction(actionEvent -> {
            File file = fileChooser.showOpenDialog(RootApplication.primaryStage);
            if (file != null) {
                playerPath1.setText(file.getAbsolutePath());
            }
        });
    }

    private void addBrowserButton2ClickHandler() {
        browserButtonPlayerPath2.setOnAction(actionEvent -> {
            File file = fileChooser.showOpenDialog(RootApplication.primaryStage);
            if (file != null) {
                playerPath2.setText(file.getAbsolutePath());
            }
        });
    }

    private void addBrowserButton3ClickHandler() {
        browserButtonPlayerPath3.setOnAction(actionEvent -> {
            File file = fileChooser.showOpenDialog(RootApplication.primaryStage);
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
            if (oldToggle != null) {
                group.selectToggle(oldToggle);
            } else {
                group.selectToggle(null);
            }
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
}
