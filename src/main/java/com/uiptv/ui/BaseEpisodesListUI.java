package com.uiptv.ui;

import com.uiptv.util.EpisodeTitleFormatter;
import com.uiptv.util.I18n;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.BookmarkChangeListener;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.SeriesWatchStateChangeListener;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.ServerUrlUtil;
import javafx.beans.Observable;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.json.JSONObject;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.StringUtils.isBlank;
import static javafx.application.Platform.runLater;

public abstract class BaseEpisodesListUI extends HBox {
    protected static final Pattern SXXEYY_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b");
    protected static final Pattern SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)|\\b(\\d{1,2})x\\d{1,3}\\b");
    protected static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b|\\b\\d{1,2}x(\\d{1,3})\\b");
    protected static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},\\s+\\d{4}\\b");
    protected static final Pattern SLASH_DATE_PATTERN = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b");
    protected static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    protected final Account account;
    protected final String categoryTitle;
    protected final String seriesId;
    protected final String seriesCategoryId;
    protected final EpisodeList channelList;

    protected final AtomicBoolean itemsLoaded = new AtomicBoolean(false);
    protected final StackPane contentStack = new StackPane();
    protected final Label emptyStateLabel = new Label();
    protected final ObservableList<EpisodeItem> allEpisodeItems = FXCollections.observableArrayList(EpisodeItem.extractor());

    protected JSONObject seasonInfo = new JSONObject();
    private String pendingTargetSeason = "";
    private String pendingTargetEpisodeId = "";
    private String pendingTargetEpisodeNumber = "";
    private String pendingTargetEpisodeName = "";

    private boolean bookmarkListenerRegistered = false;
    private final BookmarkChangeListener bookmarkChangeListener = (revision, updatedEpochMs) -> refreshBookmarkStatesAsync();
    private boolean watchStateListenerRegistered = false;
    private final SeriesWatchStateChangeListener watchStateChangeListener;

    protected BaseEpisodesListUI(Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        this.channelList = new EpisodeList();
        this.account = account;
        this.categoryTitle = categoryTitle;
        this.seriesId = isBlank(seriesId) ? "" : seriesId.trim();
        this.seriesCategoryId = isBlank(seriesCategoryId) ? "" : seriesCategoryId.trim();
        this.watchStateChangeListener = (accountId, changedSeriesId) -> {
            if (this.account != null
                    && this.account.getDbId() != null
                    && ((this.account.getDbId().equals(accountId) && this.seriesId.equals(changedSeriesId))
                    || (isBlank(accountId) && isBlank(changedSeriesId)))) {
                refreshWatchedStatesAsync();
            }
        };

    }

    protected final void finishInit() {
        initBaseLayout();
        initWidgets();
        configureEmptyStateOverlay();
        registerBookmarkListener();
        registerWatchStateListener();
        showPlaceholder(I18n.tr("autoLoadingEpisodesFor", categoryTitle));
    }

    protected abstract void initWidgets();

    protected abstract void onItemsLoaded();

    protected abstract void showPlaceholder(String text);

    protected abstract void setEmptyState(String message, boolean empty);

    protected abstract void clearEpisodesAndRefreshTabs();

    protected abstract void onBookmarksRefreshed();

    protected abstract void onWatchedStatesRefreshed();

    public void setItems(EpisodeList newChannelList) {
        if (newChannelList == null) return;
        if (newChannelList.episodes == null || newChannelList.episodes.isEmpty()) {
            return;
        }
        itemsLoaded.set(true);
        syncEpisodeList(newChannelList);
        syncSeasonInfo(newChannelList);
        Set<String> bookmarkKeys = loadBookmarkKeysForAccount();
        SeriesWatchState watchedState = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), seriesCategoryId, seriesId);
        navigateToLastWatched(watchedState);
        List<EpisodeItem> items = buildEpisodeItems(newChannelList, bookmarkKeys, watchedState);
        runLater(() -> {
            allEpisodeItems.setAll(items);
            onItemsLoaded();
            applyPendingNavigation();
        });
    }

    public void setLoadingComplete() {
        runLater(() -> {
            if (!itemsLoaded.get()) {
                setEmptyState(I18n.tr("autoNothingFoundFor", categoryTitle), true);
            }
        });
    }

    public void navigateToLastWatched(SeriesWatchState state) {
        if (state == null) {
            pendingTargetSeason = "";
            pendingTargetEpisodeId = "";
            pendingTargetEpisodeNumber = "";
            pendingTargetEpisodeName = "";
            return;
        }
        pendingTargetSeason = normalizeNumber(state.getSeason());
        pendingTargetEpisodeId = safe(state.getEpisodeId());
        pendingTargetEpisodeNumber = state.getEpisodeNum() > 0 ? String.valueOf(state.getEpisodeNum()) : "";
        pendingTargetEpisodeName = safe(state.getEpisodeName());
        applyPendingNavigation();
    }

    protected void navigateToEpisodeTarget(String season, String episodeId, String episodeNumber, String episodeName) {
        // Optional in subclasses.
    }

    protected EpisodeItem findBestEpisodeMatch(String season, String episodeId, String episodeNumber, String episodeName) {
        if (allEpisodeItems.isEmpty()) {
            return null;
        }

        String normalizedSeason = normalizeNumber(season);
        String normalizedEpisodeNo = normalizeNumber(episodeNumber);
        String normalizedEpisodeId = safe(episodeId).trim();
        String normalizedEpisodeName = normalizeTitle(cleanEpisodeTitle(episodeName));

        EpisodeItem match = findWatchedEpisodeMatch(normalizedSeason);
        if (match != null) return match;
        match = findEpisodeById(normalizedEpisodeId);
        if (match != null) return match;
        match = findEpisodeBySeasonAndNumber(normalizedSeason, normalizedEpisodeNo);
        if (match != null) return match;
        match = findEpisodeByName(normalizedEpisodeName);
        if (match != null) return match;
        match = findEpisodeBySeason(normalizedSeason);
        return match != null ? match : allEpisodeItems.get(0);
    }

    private void applyPendingNavigation() {
        if (allEpisodeItems.isEmpty()) {
            return;
        }
        if (isBlank(pendingTargetSeason) && isBlank(pendingTargetEpisodeId)
                && isBlank(pendingTargetEpisodeNumber) && isBlank(pendingTargetEpisodeName)) {
            return;
        }
        runLater(() -> navigateToEpisodeTarget(
                pendingTargetSeason,
                pendingTargetEpisodeId,
                pendingTargetEpisodeNumber,
                pendingTargetEpisodeName
        ));
    }

    protected void initBaseLayout() {
        setPadding(new Insets(5));
        setSpacing(6);
        setMinWidth(0);
        setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        setMaxWidth(Double.MAX_VALUE);
    }

    private void configureEmptyStateOverlay() {
        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setMaxWidth(Double.MAX_VALUE);
        emptyStateLabel.getStyleClass().add("empty-state-label");
        emptyStateLabel.setManaged(false);
        emptyStateLabel.setVisible(false);
        StackPane.setAlignment(emptyStateLabel, Pos.CENTER);
        if (!contentStack.getChildren().contains(emptyStateLabel)) {
            contentStack.getChildren().add(emptyStateLabel);
        }
        HBox.setHgrow(contentStack, Priority.ALWAYS);
        if (!getChildren().contains(contentStack)) {
            getChildren().add(contentStack);
        }
    }

    protected void registerBookmarkListener() {
        if (bookmarkListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
        bookmarkListenerRegistered = true;
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                unregisterBookmarkListener();
                releaseTransientState();
            } else if (!bookmarkListenerRegistered) {
                BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
                bookmarkListenerRegistered = true;
                refreshBookmarkStatesAsync();
            }
        });
    }

    protected void registerWatchStateListener() {
        if (watchStateListenerRegistered) {
            return;
        }
        SeriesWatchStateService.getInstance().addChangeListener(watchStateChangeListener);
        watchStateListenerRegistered = true;
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                unregisterWatchStateListener();
                releaseTransientState();
            } else if (!watchStateListenerRegistered) {
                SeriesWatchStateService.getInstance().addChangeListener(watchStateChangeListener);
                watchStateListenerRegistered = true;
                refreshWatchedStatesAsync();
            }
        });
    }

    protected void releaseTransientState() {
        allEpisodeItems.clear();
        seasonInfo = new JSONObject();
        channelList.episodes.clear();
        channelList.seasonInfo = null;
    }

    protected void unregisterBookmarkListener() {
        if (!bookmarkListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().removeChangeListener(bookmarkChangeListener);
        bookmarkListenerRegistered = false;
    }

    protected void unregisterWatchStateListener() {
        if (!watchStateListenerRegistered) {
            return;
        }
        SeriesWatchStateService.getInstance().removeChangeListener(watchStateChangeListener);
        watchStateListenerRegistered = false;
    }

    protected void refreshBookmarkStatesAsync() {
        if (allEpisodeItems.isEmpty()) {
            return;
        }
        new Thread(() -> {
            List<Bookmark> bookmarks = BookmarkService.getInstance().read();
            Set<String> bookmarkKeys = bookmarks.stream()
                    .filter(b -> account.getAccountName().equals(b.getAccountName()))
                    .map(b -> bookmarkIdentityKey(b.getChannelId(), b.getChannelName()))
                    .collect(Collectors.toSet());
            runLater(() -> {
                for (EpisodeItem item : allEpisodeItems) {
                    boolean isBookmarked = bookmarkKeys.contains(bookmarkIdentityKey(item.getEpisodeId(), item.getEpisodeName()));
                    item.setBookmarked(isBookmarked);
                }
                onBookmarksRefreshed();
            });
        }, "episodes-bookmark-refresh").start();
    }

    protected void refreshWatchedStatesAsync() {
        if (allEpisodeItems.isEmpty() || account == null || isBlank(account.getDbId()) || isBlank(seriesId)) {
            return;
        }
        new Thread(() -> {
            SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), seriesCategoryId, seriesId);
            runLater(() -> {
                for (EpisodeItem item : allEpisodeItems) {
                    item.setWatched(SeriesWatchStateService.getInstance().isMatchingEpisode(
                            state,
                            item.getEpisodeId(),
                            item.getSeason(),
                            item.getEpisodeNumber(),
                            item.getEpisodeName()
                    ));
                }
                onWatchedStatesRefreshed();
            });
        }, "episodes-watch-refresh").start();
    }

    protected String bookmarkIdentityKey(String channelId, String channelName) {
        return (channelId == null ? "" : channelId.trim()) + "|" + (channelName == null ? "" : channelName.trim().toLowerCase());
    }

    protected String bookmarkKey(String categoryTitleValue, String channelId, String channelName) {
        String category = categoryTitleValue == null ? "" : categoryTitleValue.trim().toLowerCase();
        String id = channelId == null ? "" : channelId.trim();
        String name = channelName == null ? "" : channelName.trim().toLowerCase();
        return category + "|" + id + "|" + name;
    }

    protected Set<String> loadBookmarkKeysForAccount() {
        return BookmarkService.getInstance().read().stream()
                .filter(b -> account.getAccountName().equals(b.getAccountName()))
                .map(b -> bookmarkKey(b.getCategoryTitle(), b.getChannelId(), b.getChannelName()))
                .collect(Collectors.toSet());
    }

    protected void markEpisodeAsWatched(EpisodeItem item) {
        if (item == null || isBlank(seriesId) || account == null) {
            return;
        }
        new Thread(() -> {
            account.setAction(Account.AccountAction.series);
            SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                    account,
                    seriesCategoryId,
                    seriesId,
                    item.getEpisodeId(),
                    item.getEpisodeName(),
                    item.getSeason(),
                    item.getEpisodeNumber()
            );
            refreshWatchedStatesAsync();
        }, "episodes-mark-watched").start();
    }

    protected void clearWatchedMarker() {
        if (account == null || isBlank(account.getDbId()) || isBlank(seriesId)) {
            return;
        }
        new Thread(() -> {
            SeriesWatchStateService.getInstance().clearSeriesLastWatched(account.getDbId(), seriesCategoryId, seriesId);
            refreshWatchedStatesAsync();
        }, "episodes-clear-watched").start();
    }

    protected void play(EpisodeItem item, String playerPath) {
        if (item == null) {
            return;
        }
        account.setAction(Account.AccountAction.series);
        SeriesWatchStateService.getInstance().markSeriesEpisodeManualIfNewer(
                account,
                seriesCategoryId,
                seriesId,
                item.getEpisodeId(),
                item.getEpisodeName(),
                item.getSeason(),
                item.getEpisodeNumber()
        );
        refreshWatchedStatesAsync();
        Channel channel = new Channel();
        channel.setChannelId(item.getEpisodeId());
        channel.setName(item.getEpisodeName());
        channel.setCmd(item.getCmd());
        channel.setSeason(item.getSeason());
        channel.setEpisodeNum(item.getEpisodeNumber());
        channel.setLogo(item.getLogo());
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(account, channel, playerPath)
                .series(seriesId, seriesCategoryId)
                .channelId(item.getEpisodeId())
                .categoryId(seriesCategoryId)
                .errorPrefix(I18n.tr("autoErrorPlayingEpisodePrefix")));
    }

    protected String inferSeason(Episode episode) {
        if (episode == null) return "1";
        String explicit = normalizeNumber(episode.getSeason());
        if (!isBlank(explicit)) return explicit;
        String title = safe(episode.getTitle());
        Matcher sxey = SXXEYY_PATTERN.matcher(title);
        if (sxey.find()) {
            return normalizeNumber(sxey.group(1));
        }
        Matcher m = SEASON_PATTERN.matcher(title);
        if (m.find()) {
            return normalizeNumber(firstNonBlank(m.group(1), m.group(2), m.group(3)));
        }
        return "1";
    }

    protected String inferEpisodeNumber(Episode episode) {
        if (episode == null) return "";
        String explicit = normalizeNumber(episode.getEpisodeNum());
        if (!isBlank(explicit)) return explicit;
        String title = safe(episode.getTitle());
        Matcher sxey = SXXEYY_PATTERN.matcher(title);
        if (sxey.find()) {
            return normalizeNumber(sxey.group(2));
        }
        Matcher m = EPISODE_PATTERN.matcher(title);
        if (m.find()) {
            return normalizeNumber(firstNonBlank(m.group(1), m.group(2), m.group(3)));
        }
        return "";
    }

    protected String cleanEpisodeTitle(String title) {
        String value = safe(title);
        String previous;
        do {
            previous = value;
            value = value
                    .replaceAll("(?i)^\\s*season\\s*\\d+\\s*[-:]?\\s*", "")
                    .replaceAll("(?i)^\\s*s\\d+\\s*[-:]?\\s*", "")
                    .replaceAll("(?i)^\\s*episode\\s*\\d+\\s*[-:]?\\s*", "")
                    .replaceAll("(?i)^\\s*ep\\.?\\s*\\d+\\s*[-:]?\\s*", "")
                    .replaceAll("(?i)^\\s*e\\d+\\s*[-:]?\\s*", "")
                    .trim();
        } while (!value.equals(previous));
        return isGenericEpisodeTitle(value) ? "" : value;
    }

    protected String cleanEpisodeTitleWithPlot(String title, String plot) {
        String cleaned = cleanEpisodeTitle(title);
        return stripAppendedPlot(cleaned, plot);
    }

    protected String stripAppendedPlot(String title, String plot) {
        if (isBlank(title) || isBlank(plot)) {
            return title;
        }
        String trimmedPlot = plot.trim();
        if (trimmedPlot.length() < 15) {
            return title;
        }
        int idx = indexOfIgnoreCase(title, trimmedPlot);
        if (idx <= 0) {
            return title;
        }
        String before = title.substring(0, idx).trim();
        if (before.isEmpty()) {
            return title;
        }
        before = before.replaceAll("[-:|]+\\s*$", "").trim();
        return before.isEmpty() ? title : before;
    }

    protected int indexOfIgnoreCase(String text, String needle) {
        if (text == null || needle == null) {
            return -1;
        }
        return text.toLowerCase(Locale.ENGLISH).indexOf(needle.toLowerCase(Locale.ENGLISH));
    }

    protected int parseNumberOrDefault(String value) {
        try {
            if (isBlank(value)) return 1;
            return Integer.parseInt(value);
        } catch (Exception _) {
            // Invalid numeric labels should fall back to the default season/episode number.
            return 1;
        }
    }

    protected String normalizeNumber(String value) {
        if (isBlank(value)) return "";
        String parsed = value.replaceAll("[^0-9]", "");
        if (isBlank(parsed)) return "";
        try {
            return String.valueOf(Integer.parseInt(parsed));
        } catch (Exception _) {
            // Invalid numeric labels should behave like an unknown season/episode number.
            return "";
        }
    }

    protected String normalizeTitle(String value) {
        return safe(value).toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    protected String shortDateOnly(String value) {
        String v = safe(value).trim();
        if (isBlank(v)) {
            return "";
        }
        LocalDate parsed = parseDate(v);
        if (parsed != null) {
            return I18n.formatDate(parsed);
        }
        if (v.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            return v.substring(0, 10);
        }
        int t = v.indexOf('T');
        if (t > 0) {
            String left = v.substring(0, t);
            if (left.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return left;
            }
        }
        Matcher monthMatcher = MONTH_DATE_PATTERN.matcher(v);
        if (monthMatcher.find()) {
            return monthMatcher.group();
        }
        Matcher slashMatcher = SLASH_DATE_PATTERN.matcher(v);
        if (slashMatcher.find()) {
            return slashMatcher.group();
        }
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(v);
        if (isoMatcher.find()) {
            return isoMatcher.group();
        }
        if (v.contains(",")) {
            String[] parts = v.split(",");
            if (parts.length >= 2) {
                return parts[0].trim() + ", " + parts[1].trim();
            }
        }
        return v;
    }

    protected String buildEpisodeDisplayTitle(String season, String episodeNo, String title) {
        return EpisodeTitleFormatter.buildEpisodeDisplayTitle(season, episodeNo, title);
    }

    protected boolean isGenericEpisodeTitle(String title) {
        return EpisodeTitleFormatter.isGenericEpisodeTitle(title);
    }

    protected LocalDate parseDate(String value) {
        String input = safe(value).trim();
        if (isBlank(input)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(input).toLocalDate();
        } catch (Exception _) {
            // Offset timestamps are optional; try the plain date patterns next.
        }
        String[] patterns = new String[]{
                "yyyy-MM-dd",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "MMM d, yyyy",
                "MMMM d, yyyy",
                "d MMM yyyy",
                "d MMMM yyyy",
                "M/d/yyyy",
                "MM/dd/yyyy",
                "d/M/yyyy",
                "dd/MM/yyyy"
        };
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(input, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
            } catch (DateTimeParseException _) {
                // Try the next date parser below.
            }
        }
        Matcher iso = ISO_DATE_PATTERN.matcher(input);
        if (iso.find()) {
            try {
                return LocalDate.parse(iso.group(), DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH));
            } catch (DateTimeParseException _) {
                // Try the next date parser below.
            }
        }
        Matcher month = MONTH_DATE_PATTERN.matcher(input);
        if (month.find()) {
            String candidate = month.group();
            try {
                return LocalDate.parse(candidate, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
            } catch (DateTimeParseException _) {
                // Try the next date parser below.
            }
            try {
                return LocalDate.parse(candidate, DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
            } catch (DateTimeParseException _) {
                // Give up on parsing this month-format variant.
            }
        }
        return null;
    }

    protected String normalizeImageUrl(String imageUrl) {
        if (isBlank(imageUrl)) {
            return "";
        }
        String value = trimWrappedImageUrl(imageUrl.trim().replace("\\/", "/"));
        if (isBlank(value)) {
            return "";
        }
        if (isAbsoluteImageUrl(value) || isInlineImageUrl(value)) {
            return value;
        }
        BaseUriParts base = baseUriParts();
        if (value.startsWith("//")) {
            return base.scheme() + ":" + value;
        }
        if (value.startsWith("/")) {
            if (!isBlank(base.host())) {
                return buildBaseUrl(base) + value;
            }
            return value;
        }
        if (value.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) {
            return base.scheme() + "://" + value;
        }
        if (!isBlank(base.host())) {
            return buildBaseUrl(base) + "/" + stripLeadingRelativePrefix(value);
        }
        return localServerOrigin() + "/" + stripLeadingRelativePrefix(value);
    }

    private void syncEpisodeList(EpisodeList newChannelList) {
        this.channelList.episodes.clear();
        this.channelList.episodes.addAll(newChannelList.episodes);
        this.channelList.seasonInfo = newChannelList.seasonInfo;
    }

    private void syncSeasonInfo(EpisodeList newChannelList) {
        this.seasonInfo = cloneSeasonInfo(newChannelList.seasonInfo);
        if (isBlank(this.seasonInfo.optString("name", ""))) {
            this.seasonInfo.put("name", categoryTitle);
        }
    }

    private JSONObject cloneSeasonInfo(com.uiptv.shared.SeasonInfo value) {
        if (value == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(value.toJson());
        } catch (Exception _) {
            // Episode snapshots are optional; rebuild season info from other fields when parsing fails.
            return new JSONObject();
        }
    }

    private List<EpisodeItem> buildEpisodeItems(EpisodeList newChannelList, Set<String> bookmarkKeys, SeriesWatchState watchedState) {
        List<EpisodeItem> items = new ArrayList<>();
        newChannelList.episodes.forEach(episode -> items.add(buildEpisodeItem(episode, bookmarkKeys, watchedState)));
        return items;
    }

    private EpisodeItem buildEpisodeItem(Episode episode, Set<String> bookmarkKeys, SeriesWatchState watchedState) {
        boolean isBookmarked = bookmarkKeys.contains(bookmarkKey(categoryTitle, episode.getId(), episode.getTitle()));
        String logo = episode.getInfo() != null ? normalizeImageUrl(episode.getInfo().getMovieImage()) : "";
        String tmdbId = episode.getInfo() != null ? episode.getInfo().getTmdbId() : "";
        String season = inferSeason(episode);
        String episodeNo = inferEpisodeNumber(episode);
        boolean isWatched = SeriesWatchStateService.getInstance().isMatchingEpisode(
                watchedState, episode.getId(), season, episodeNo, episode.getTitle());
        String plot = episode.getInfo() != null ? safe(episode.getInfo().getPlot()) : "";
        String cleanTitle = cleanEpisodeTitleWithPlot(episode.getTitle(), plot);
        String displayTitle = buildEpisodeDisplayTitle(season, episodeNo, cleanTitle);
        String releaseDate = episode.getInfo() != null ? safe(episode.getInfo().getReleaseDate()) : "";
        String rating = episode.getInfo() != null ? safe(episode.getInfo().getRating()) : "";
        return new EpisodeItem(
                new SimpleStringProperty(displayTitle),
                new SimpleStringProperty(episode.getId()),
                new SimpleStringProperty(episode.getCmd()),
                isBookmarked,
                isWatched,
                new SimpleStringProperty(logo),
                new SimpleStringProperty(tmdbId),
                new SimpleStringProperty(season),
                new SimpleStringProperty(episodeNo),
                new SimpleStringProperty(plot),
                new SimpleStringProperty(releaseDate),
                new SimpleStringProperty(rating),
                episode
        );
    }

    private EpisodeItem findWatchedEpisodeMatch(String normalizedSeason) {
        if (!isBlank(normalizedSeason)) {
            for (EpisodeItem item : allEpisodeItems) {
                if (item.isWatched() && normalizedSeason.equals(normalizeNumber(item.getSeason()))) {
                    return item;
                }
            }
        }
        for (EpisodeItem item : allEpisodeItems) {
            if (item.isWatched()) {
                return item;
            }
        }
        return null;
    }

    private EpisodeItem findEpisodeById(String normalizedEpisodeId) {
        if (isBlank(normalizedEpisodeId)) {
            return null;
        }
        for (EpisodeItem item : allEpisodeItems) {
            if (normalizedEpisodeId.equals(safe(item.getEpisodeId()).trim())) {
                return item;
            }
        }
        return null;
    }

    private EpisodeItem findEpisodeBySeasonAndNumber(String normalizedSeason, String normalizedEpisodeNo) {
        if (isBlank(normalizedSeason) || isBlank(normalizedEpisodeNo)) {
            return null;
        }
        for (EpisodeItem item : allEpisodeItems) {
            if (normalizedSeason.equals(normalizeNumber(item.getSeason()))
                    && normalizedEpisodeNo.equals(normalizeNumber(item.getEpisodeNumber()))) {
                return item;
            }
        }
        return null;
    }

    private EpisodeItem findEpisodeByName(String normalizedEpisodeName) {
        if (isBlank(normalizedEpisodeName)) {
            return null;
        }
        for (EpisodeItem item : allEpisodeItems) {
            String itemName = normalizeTitle(cleanEpisodeTitle(item.getEpisodeName()));
            if (normalizedEpisodeName.equals(itemName)) {
                return item;
            }
        }
        return null;
    }

    private EpisodeItem findEpisodeBySeason(String normalizedSeason) {
        if (isBlank(normalizedSeason)) {
            return null;
        }
        for (EpisodeItem item : allEpisodeItems) {
            if (normalizedSeason.equals(normalizeNumber(item.getSeason()))) {
                return item;
            }
        }
        return null;
    }

    private String trimWrappedImageUrl(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private boolean isAbsoluteImageUrl(String value) {
        return value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    }

    private boolean isInlineImageUrl(String value) {
        return value.startsWith("data:") || value.startsWith("blob:") || value.startsWith("file:");
    }

    private BaseUriParts baseUriParts() {
        URI base = resolveBaseUri();
        if (base == null) {
            return new BaseUriParts("https", "", -1);
        }
        return new BaseUriParts(
                isBlank(base.getScheme()) ? "https" : base.getScheme(),
                isBlank(base.getHost()) ? "" : base.getHost(),
                base.getPort()
        );
    }

    private String buildBaseUrl(BaseUriParts base) {
        return base.scheme() + "://" + base.host() + (base.port() > 0 ? ":" + base.port() : "");
    }

    private String stripLeadingRelativePrefix(String value) {
        return value.replaceFirst("^\\./", "");
    }

    protected URI resolveBaseUri() {
        List<String> candidates = List.of(account.getServerPortalUrl(), account.getUrl());
        for (String candidate : candidates) {
            if (isBlank(candidate)) continue;
            try {
                URI uri = URI.create(candidate.trim());
                if (!isBlank(uri.getHost())) {
                    return uri;
                }
                if (isBlank(uri.getScheme())) {
                    URI withScheme = URI.create("http://" + candidate.trim());
                    if (!isBlank(withScheme.getHost())) {
                        return withScheme;
                    }
                }
            } catch (Exception _) {
                // Missing/invalid season metadata should not prevent the remaining items from loading.
            }
        }
        return null;
    }

    protected String localServerOrigin() {
        return ServerUrlUtil.getLocalServerUrl();
    }

    private record BaseUriParts(String scheme, String host, int port) {
    }

    protected String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return "";
    }

    protected String safe(String value) {
        return value == null ? "" : value;
    }

    protected void mergeMissing(JSONObject target, JSONObject source, String key) {
        if (target == null || source == null || isBlank(key)) {
            return;
        }
        if (isBlank(target.optString(key, "")) && !isBlank(source.optString(key, ""))) {
            target.put(key, source.optString(key, ""));
        }
    }

    public static class EpisodeItem {
        private final SimpleStringProperty episodeName;
        private final SimpleStringProperty episodeId;
        private final SimpleStringProperty cmd;
        private final SimpleBooleanProperty bookmarked;
        private final SimpleBooleanProperty watched;
        private final SimpleStringProperty logo;
        private final SimpleStringProperty tmdbId;
        private final SimpleStringProperty season;
        private final SimpleStringProperty episodeNumber;
        private final SimpleStringProperty plot;
        private final SimpleStringProperty releaseDate;
        private final SimpleStringProperty rating;
        private final Episode episode;

        @SuppressWarnings("java:S107")
        public EpisodeItem(SimpleStringProperty episodeName,
                          SimpleStringProperty episodeId,
                          SimpleStringProperty cmd,
                          boolean isBookmarked,
                          boolean isWatched,
                          SimpleStringProperty logo,
                          SimpleStringProperty tmdbId,
                          SimpleStringProperty season,
                          SimpleStringProperty episodeNumber,
                          SimpleStringProperty plot,
                          SimpleStringProperty releaseDate,
                          SimpleStringProperty rating,
                          Episode episode) {
            this.episodeName = episodeName;
            this.episodeId = episodeId;
            this.cmd = cmd;
            this.bookmarked = new SimpleBooleanProperty(isBookmarked);
            this.watched = new SimpleBooleanProperty(isWatched);
            this.logo = logo;
            this.tmdbId = tmdbId;
            this.season = season;
            this.episodeNumber = episodeNumber;
            this.plot = plot;
            this.releaseDate = releaseDate;
            this.rating = rating;
            this.episode = episode;
        }

        public static javafx.util.Callback<EpisodeItem, Observable[]> extractor() {
            return item -> new Observable[]{item.bookmarkedProperty(), item.watchedProperty(), item.logoProperty(), item.plotProperty(), item.releaseDateProperty(), item.ratingProperty()};
        }

        public String getEpisodeName() {
            return episodeName.get();
        }

        public String getEpisodeId() {
            return episodeId.get();
        }

        public String getCmd() {
            return cmd.get();
        }

        public boolean isBookmarked() {
            return bookmarked.get();
        }

        public void setBookmarked(boolean bookmarked) {
            this.bookmarked.set(bookmarked);
        }

        public boolean isWatched() {
            return watched.get();
        }

        public void setWatched(boolean watched) {
            this.watched.set(watched);
        }

        public String getLogo() {
            return logo.get();
        }

        public void setLogo(String logo) {
            this.logo.set(logo == null ? "" : logo);
        }

        public String getTmdbId() {
            return tmdbId.get();
        }

        public String getSeason() {
            return season.get();
        }

        public String getEpisodeNumber() {
            return episodeNumber.get();
        }

        public String getPlot() {
            return plot.get();
        }

        public void setPlot(String value) {
            this.plot.set(value == null ? "" : value);
        }

        public String getReleaseDate() {
            return releaseDate.get();
        }

        public void setReleaseDate(String value) {
            this.releaseDate.set(value == null ? "" : value);
        }

        public String getRating() {
            return rating.get();
        }

        public void setRating(String value) {
            this.rating.set(value == null ? "" : value);
        }

        public SimpleBooleanProperty bookmarkedProperty() {
            return bookmarked;
        }

        public SimpleBooleanProperty watchedProperty() {
            return watched;
        }

        public SimpleStringProperty logoProperty() {
            return logo;
        }

        public SimpleStringProperty plotProperty() {
            return plot;
        }

        public SimpleStringProperty releaseDateProperty() {
            return releaseDate;
        }

        public SimpleStringProperty ratingProperty() {
            return rating;
        }

        public Episode getEpisode() {
            return episode;
        }

        public SimpleStringProperty episodeNameProperty() {
            return episodeName;
        }
    }
}
