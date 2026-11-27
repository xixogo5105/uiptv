package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.AccountApplicationService;
import com.uiptv.application.BulkAccountImportApplicationService;
import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogMode;
import com.uiptv.application.CatalogPagedChannelsResult;
import com.uiptv.application.CatalogWebChannelsQuery;
import com.uiptv.application.BookmarkApplicationService;
import com.uiptv.application.WatchingNowApplicationService;
import com.uiptv.application.WatchingNowSeriesRow;
import com.uiptv.application.WatchingNowVodRow;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.AccountResolver;
import com.uiptv.util.I18n;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public final class HttpDesktopApiV1GatewayServer implements HttpHandler {
    private static final Set<String> DESKTOP_TRANSLATION_KEYS = Set.of(
            "bookmarkRecent", "autoFavoritesEmptyDetail", "autoRecentItemsEmptyDetail",
            "autoManageCategories", "autoCategoryName", "autoAdd", "autoRemove", "autoManage",
            "autoCancel", "autoClear", "commonAll", "commonClose", "commonConfirm", "commonSearch",
            "configClearRecent", "configRecentClearConfirm", "configRecentCleared", "configRecentClearFailed",
            "autoImportBulkAccounts", "bulkImportSourceType", "bulkImportSourceDescription",
            "bulkImportDataTitle", "bulkImportDataDescription", "autoGroupAccountsByMACAddress",
            "autoGroupAccountsByUsernamePassword", "autoGroupConvertedM3UAccountsByUrl",
            "autoWherePossibleConvertM3UToXtreme", "autoStartVerificationAfterParsing", "parseAndSave",
            "parseMultipleAccountsInputHint", "autoInputCannotBeEmpty", "autoErrorParsingOrSavingAccounts",
            "autoSeasonNumber",
            "autoAccountsParsedAndSaved", "autoUiptvLogs", "autoCopy", "autoWebLogs", "autoDetach",
            "autoAttach", "autoLogsDetachedInASeparateWindow", "autoAboutUIPTV", "autoAboutUiptvTitle",
            "autoAboutUiptvTagline", "autoAboutUiptvOverview", "autoAboutUiptvWebSync",
            "autoAboutUiptvReleaseNotes", "autoAuthorXixogo5105", "autoAboutUiptvRuntime",
            "autoAboutUiptvPlatform", "autoAboutUiptvPoweredBy", "autoCopyright2024Xixogo5105",
            "autoHttpsGithubComXixogo5105Uiptv", "autoHelp", "autoClose", "autoCheckForUpdates",
            "configVideoPlayers", "configFilters", "configDarkTheme", "configDatabaseSyncTitle",
            "configWebServer", "configTmdbMetadata", "configAddPlayerPathsHint", "configWideView",
            "configResolveChainAndDeepRedirects", "configEnableThumbnails",
            "configShowBookmarkAndAccountCounts", "configLanguage", "configCacheExpiresInDays",
            "configServerPortPrompt", "configHttpsServerPortPrompt", "configAutoRunServerOnStartup",
            "configEnableHttpsServer", "configWebPlaybackProxyEnabled", "configTmdbReadAccessToken",
            "configStartServer", "configStopServer", "configTmdbApiGuideLink", "configTmdbApiKeyPageLink",
            "configEnableEpg", "configPlainTextMode", "configThemeZoom", "commonLight", "commonDark",
            "commonSystem", "commonSave", "autoRestore", "autoPlayer1", "autoPlayer2", "autoPlayer3",
            "autoEmbeddedPlayer", "configCacheExpiryPrompt",
            "configTmdbReadAccessTokenPrompt", "configDatabaseSyncDescription", "configExportDatabase",
            "configImportDatabase", "filterLockStateToggleLabel", "filterLockStateLocked",
            "filterLockStateUnlocked", "filterLockStateNotSet", "configFiltersHelpText",
            "configDefaultWebBrowserPlayer", "filterLockChangePasswordAction",
            "filterLockLockNowAction", "filterLockDisablePasswordAction",
            "filterLockDisablePasswordPrompt", "filterLockNewPasswordPrompt",
            "filterLockCurrentPasswordPrompt", "filterLockPasswordPrompt",
            "filterLockConfirmPasswordPrompt", "filterLockDisablePasswordTitle",
            "filterLockDisablePasswordWarning", "configFilterCategoriesPrompt",
            "configFilterChannelsPrompt", "filterLockHiddenCategoriesPrompt",
            "filterLockChangePasswordTitle",
            "filterLockPasswordRequirements", "filterLockPasswordSetSuccess",
            "filterLockPasswordChangeSuccess", "filterLockPasswordDisableSuccess",
            "filterLockPasswordTooShort", "filterLockPasswordConfirmMismatch",
            "filterLockPasswordInvalid", "filterLockCurrentPasswordInvalid",
            "filterLockPasswordNotSet", "autoEnableParentalLockAccess", "autoDisableParentalLockAccess",
            "autoReloadAccountsCache", "autoStartCacheReload", "autoStop", "autoRunningProgress",
            "autoQueued", "reloadStalkerPortalAccounts", "reloadXtremeAccount", "reloadM3uRemotePlaylist",
            "reloadM3uLocalPlaylist", "reloadBulkFailureHandlingTitle", "reloadBulkFailureHandlingMessage",
            "reloadBulkFailureAskEachAccount", "reloadCarryOn", "reloadMarkBadIgnoreDomain",
            "reloadMarkBadAndNext", "reloadStopAll", "reloadGlobalCallFailure",
            "reloadGlobalFailurePrompt", "reloadGlobalFailurePromptWithIgnoreDomain", "reloadNoChannelsFound",
            "reloadFailedReason", "reloadMarkedBadByUser", "reloadSkippedIgnoredDomain", "reloadDone",
            "reloadPartial", "reloadFailed", "reloadNoChannelsLoaded", "reloadDoneWithChannels",
            "reloadPartialWithChannels", "reloadCacheDisabledSkipping");

    static {
        DesktopApiLogBuffer.initialize();
    }
    private final HttpSeriesEpisodesJsonServer seriesEpisodes = new HttpSeriesEpisodesJsonServer();
    private final HttpSeriesDetailsJsonServer seriesDetails = new HttpSeriesDetailsJsonServer();
    private final HttpVodDetailsJsonServer vodDetails = new HttpVodDetailsJsonServer();
    private final HttpLiveGuideJsonServer liveGuide = new HttpLiveGuideJsonServer();
    private final HttpPlayerEpgJsonServer playerEpg = new HttpPlayerEpgJsonServer();
    private final HttpPlayerJsonServer player = new HttpPlayerJsonServer();
    private final HttpConfigJsonServer config = new HttpConfigJsonServer();
    private final HttpDesktopSettingsJsonServer settings = new HttpDesktopSettingsJsonServer();
    private final HttpDesktopServerControlJsonServer serverControl = new HttpDesktopServerControlJsonServer();
    private final HttpDesktopFilterLockJsonServer filterLock = new HttpDesktopFilterLockJsonServer();
    private final HttpDesktopBackupJsonServer backup = new HttpDesktopBackupJsonServer();
    private final HttpDesktopCacheReloadJsonServer cacheReload = new HttpDesktopCacheReloadJsonServer();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        switch (path) {
            case "/api/v1/health" -> handleHealth(exchange);
            case "/api/v1/accounts" -> handleAccounts(exchange);
            case "/api/v1/accounts/import" -> handleBulkAccountImport(exchange);
            case "/api/v1/about" -> handleAbout(exchange);
            case "/api/v1/logs" -> handleLogs(exchange);
            case "/api/v1/categories" -> handleCategories(exchange);
            case "/api/v1/channels" -> handleChannels(exchange);
            case "/api/v1/series/episodes" -> seriesEpisodes.handle(exchange);
            case "/api/v1/series/details" -> seriesDetails.handle(exchange);
            case "/api/v1/vod/details" -> vodDetails.handle(exchange);
            case "/api/v1/watching-now" -> handleWatchingNow(exchange);
            case "/api/v1/watching-now/episodes" -> handleWatchingNowEpisodes(exchange);
            case "/api/v1/epg/live" -> liveGuide.handle(exchange);
            case "/api/v1/epg/player" -> playerEpg.handle(exchange);
            case "/api/v1/player" -> player.handle(exchange);
            case "/api/v1/translations" -> handleTranslations(exchange);
            case "/api/v1/bookmark-categories" -> handleBookmarkCategories(exchange);
            case "/api/v1/bookmarks/recent" -> handleRecentBookmarks(exchange);
            case "/api/v1/bookmarks" -> handleBookmarks(exchange);
            case "/api/v1/config" -> config.handle(exchange);
            case "/api/v1/settings" -> settings.handle(exchange);
            case "/api/v1/settings/server" -> serverControl.handle(exchange);
            case "/api/v1/filter-lock" -> filterLock.handle(exchange);
            case "/api/v1/filter-lock/filters" -> filterLock.handle(exchange);
            case "/api/v1/backups/current" -> backup.handle(exchange);
            case "/api/v1/cache-reloads" -> cacheReload.handle(exchange);
            default -> {
                if (path.startsWith("/api/v1/cache-reloads/")) {
                    cacheReload.handle(exchange);
                } else {
                    writeJsonResponse(exchange, 404, "{\"error\":\"Unknown desktop API endpoint\"}");
                }
            }
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        JSONObject response = new JSONObject();
        response.put("status", "ok");
        response.put("apiVersion", "v1");
        writeJsonResponse(exchange, 200, response.toString());
    }

    private void handleAbout(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        JSONObject response = new JSONObject()
                .put("version", com.uiptv.util.VersionManager.getCurrentVersion())
                .put("javaVersion", System.getProperty("java.version", "N/A"))
                .put("vmName", System.getProperty("java.vm.name", "N/A"))
                .put("osName", System.getProperty("os.name", "N/A"))
                .put("osArch", System.getProperty("os.arch", "N/A"))
                .put("projectUrl", "https://github.com/xixogo5105/uiptv")
                .put("helpUrl", "https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md")
                .put("releaseUrl", com.uiptv.util.VersionManager.getReleaseUrl());
        generateJsonResponse(exchange, response.toString());
    }

    private void handleLogs(HttpExchange exchange) throws IOException {
        String source = getParam(exchange, "source");
        boolean webLogs = "web".equalsIgnoreCase(source);
        if (source != null && !source.isBlank() && !webLogs && !"app".equalsIgnoreCase(source)) {
            writeJsonResponse(exchange, 400, "{\"error\":\"Unsupported log source\"}");
            return;
        }
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            List<String> lines = webLogs
                    ? java.util.Arrays.stream(com.uiptv.util.WebActivityLog.readAllText().split("\\R"))
                            .filter(line -> !line.isBlank()).toList()
                    : DesktopApiLogBuffer.readAll();
            writeJsonResponse(exchange, 200, new JSONObject()
                    .put("lines", new JSONArray(lines))
                    .toString());
            return;
        }
        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            if (webLogs) {
                com.uiptv.util.WebActivityLog.clear();
            } else {
                DesktopApiLogBuffer.clear();
            }
            writeJsonResponse(exchange, 200, "{\"status\":\"ok\"}");
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET,DELETE");
        writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void handleBulkAccountImport(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        JSONObject body;
        try {
            body = new JSONObject(com.uiptv.util.ServerUtils.readRequestBodyText(exchange));
        } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
            writeJsonResponse(exchange, 413, "{\"error\":\"Request body is too large\"}");
            return;
        } catch (org.json.JSONException _) {
            writeJsonResponse(exchange, 400, "{\"error\":\"A valid JSON request body is required\"}");
            return;
        }
        String sourceType = body.optString("sourceType", "").trim();
        String data = body.optString("data", "");
        if (sourceType.isEmpty() || data.isBlank()) {
            writeJsonResponse(exchange, 400, "{\"error\":\"sourceType and data are required\"}");
            return;
        }
        for (String key : body.keySet()) {
            if (!Set.of("sourceType", "data", "groupAccounts", "convertM3uToXtreme",
                    "startVerificationAfterParsing").contains(key)) {
                writeJsonResponse(exchange, 400, "{\"error\":\"Unsupported account-import field\"}");
                return;
            }
        }
        if (body.has("groupAccounts") && !(body.opt("groupAccounts") instanceof Boolean)
                || body.has("convertM3uToXtreme") && !(body.opt("convertM3uToXtreme") instanceof Boolean)
                || body.has("startVerificationAfterParsing")
                && !(body.opt("startVerificationAfterParsing") instanceof Boolean)) {
            writeJsonResponse(exchange, 400, "{\"error\":\"Import options must be boolean\"}");
            return;
        }
        List<com.uiptv.service.AccountResolver.AccountRow> accounts;
        try {
            accounts = new BulkAccountImportApplicationService().importAccounts(
                    sourceType,
                    data,
                    body.optBoolean("groupAccounts", true),
                    body.optBoolean("convertM3uToXtreme", true));
        } catch (IllegalArgumentException error) {
            writeJsonResponse(exchange, 400, new JSONObject().put("error", error.getMessage()).toString());
            return;
        } catch (Exception error) {
            com.uiptv.util.AppLog.addErrorLog(HttpDesktopApiV1GatewayServer.class,
                    "Bulk account import failed (" + error.getClass().getSimpleName() + ")");
            writeJsonResponse(exchange, 422, "{\"error\":\"Unable to parse or save the supplied accounts\"}");
            return;
        }
        if (accounts.isEmpty()) {
            writeJsonResponse(exchange, 422, "{\"error\":\"No accounts could be parsed and saved\"}");
            return;
        }
        JSONArray items = new JSONArray();
        accounts.stream().map(DesktopApiV1Dtos::account).forEach(items::put);
        JSONArray verificationAccountIds = new JSONArray();
        if (body.optBoolean("startVerificationAfterParsing", true)) {
            accounts.stream().map(com.uiptv.service.AccountResolver.AccountRow::getDbId)
                    .forEach(verificationAccountIds::put);
        }
        writeJsonResponse(exchange, 201, new JSONObject()
                .put("items", items)
                .put("count", items.length())
                .put("verificationAccountIds", verificationAccountIds)
                .toString());
    }

    private void handleCategories(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        String accountId = getParam(exchange, "accountId");
        CatalogMode mode = parseMode(exchange);
        if (accountId == null || accountId.isBlank() || mode == null) {
            writeJsonResponse(exchange, 400, "{\"error\":\"accountId and a valid mode are required\"}");
            return;
        }
        if (AccountService.getInstance().getById(accountId) == null) {
            writeJsonResponse(exchange, 404, "{\"error\":\"Account not found\"}");
            return;
        }
        JSONArray response = new JSONArray();
        List<Category> categories = CatalogApplicationService.getInstance().listCategories(accountId, mode);
        categories.stream().map(DesktopApiV1Dtos::category).forEach(response::put);
        generateJsonResponse(exchange, response.toString());
    }

    private void handleChannels(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        String accountId = getParam(exchange, "accountId");
        String categoryDbId = getParam(exchange, "categoryId");
        CatalogMode mode = parseMode(exchange);
        Integer page = parsePositiveInt(getParam(exchange, "page"), 1);
        Integer pageSize = parsePositiveInt(getParam(exchange, "pageSize"), 100);
        Integer apiOffset = parseNonNegativeInt(getParam(exchange, "apiOffset"), 0);
        if (accountId == null || accountId.isBlank()
                || categoryDbId == null || categoryDbId.isBlank()
                || mode == null || page == null || pageSize == null || pageSize > 200 || apiOffset == null) {
            writeJsonResponse(exchange, 400,
                    "{\"error\":\"accountId, categoryId, valid mode, and bounded pagination are required\"}");
            return;
        }
        if (AccountService.getInstance().getById(accountId) == null) {
            writeJsonResponse(exchange, 404, "{\"error\":\"Account not found\"}");
            return;
        }

        CatalogPagedChannelsResult result;
        try {
            result = CatalogApplicationService.getInstance().listWebChannels(
                    new CatalogWebChannelsQuery(accountId, mode, categoryDbId, getParam(exchange, "movieId"),
                            page, pageSize, 1, apiOffset)
            );
        } catch (IOException e) {
            com.uiptv.util.AppLog.addErrorLog(HttpDesktopApiV1GatewayServer.class,
                    "Desktop channel listing failed (" + e.getClass().getSimpleName() + ")");
            writeJsonResponse(exchange, 502, "{\"error\":\"Unable to load channels from this account\"}");
            return;
        }
        JSONArray items = new JSONArray();
        result.items().stream()
                .map(channel -> DesktopApiV1Dtos.channel(
                        channel,
                        channel.getDbId() == null || channel.getDbId().isBlank()
                                ? DesktopPlaybackSelectionStore.getInstance().register(accountId, categoryDbId, channel)
                                : ""))
                .forEach(items::put);
        JSONObject response = new JSONObject()
                .put("items", items)
                .put("nextPage", result.nextPage())
                .put("hasMore", result.hasMore())
                .put("apiOffset", result.apiOffset());
        generateJsonResponse(exchange, response.toString());
    }

    private CatalogMode parseMode(HttpExchange exchange) {
        String rawMode = getParam(exchange, "mode");
        if (rawMode == null || rawMode.isBlank()) {
            return CatalogMode.ITV;
        }
        try {
            return CatalogMode.valueOf(rawMode.trim().toUpperCase());
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private Integer parsePositiveInt(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(rawValue);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private Integer parseNonNegativeInt(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(rawValue);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private boolean requireGet(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", "GET");
        writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
        return false;
    }

    private void handleBookmarkCategories(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            writeBookmarkCategories(exchange);
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            JSONObject body;
            try {
                body = new JSONObject(com.uiptv.util.ServerUtils.readRequestBodyText(exchange));
            } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
                writeJsonResponse(exchange, 413, "{\"error\":\"Request body is too large\"}");
                return;
            } catch (org.json.JSONException _) {
                writeJsonResponse(exchange, 400, "{\"error\":\"A valid JSON request body is required\"}");
                return;
            }
            String name = body.optString("name", "").trim();
            if (name.isEmpty()) {
                writeJsonResponse(exchange, 400, "{\"error\":\"name is required\"}");
                return;
            }
            BookmarkApplicationService.getInstance().addBookmarkCategory(name);
            writeJsonResponse(exchange, 201, "{\"status\":\"created\"}");
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            String categoryId = getParam(exchange, "categoryId");
            if (categoryId == null || categoryId.isBlank()) {
                writeJsonResponse(exchange, 400, "{\"error\":\"categoryId is required\"}");
                return;
            }
            if (!BookmarkApplicationService.getInstance().removeBookmarkCategory(categoryId)) {
                writeJsonResponse(exchange, 404, "{\"error\":\"Bookmark category not found\"}");
                return;
            }
            writeJsonResponse(exchange, 200, "{\"status\":\"deleted\"}");
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET,POST,DELETE");
        writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void writeBookmarkCategories(HttpExchange exchange) throws IOException {
        JSONArray categories = new JSONArray();
        List<BookmarkCategory> savedCategories = BookmarkApplicationService.getInstance().listCategories();
        savedCategories.stream().map(DesktopApiV1Dtos::bookmarkCategory).forEach(categories::put);
        generateJsonResponse(exchange, categories.toString());
    }

    private void handleTranslations(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        String rawKeys = getParam(exchange, "keys");
        if (rawKeys == null || rawKeys.isBlank()) {
            writeJsonResponse(exchange, 400, "{\"error\":\"keys are required\"}");
            return;
        }
        String[] keys = rawKeys.split(",", -1);
        if (keys.length > DESKTOP_TRANSLATION_KEYS.size()) {
            writeJsonResponse(exchange, 400, "{\"error\":\"Too many translation keys\"}");
            return;
        }
        JSONObject translations = new JSONObject();
        for (String rawKey : keys) {
            String key = rawKey.trim();
            if (!DESKTOP_TRANSLATION_KEYS.contains(key)) {
                writeJsonResponse(exchange, 400, "{\"error\":\"Unsupported translation key\"}");
                return;
            }
            translations.put(key, I18n.tr(key));
        }
        generateJsonResponse(exchange, translations.toString());
    }

    private void handleRecentBookmarks(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            JSONArray items = new JSONArray();
            List<Bookmark> bookmarks = BookmarkApplicationService.getInstance().listRecentBookmarks();
            bookmarks.stream().map(DesktopApiV1Dtos::bookmark).forEach(items::put);
            writeJsonResponse(exchange, 200, new JSONObject()
                    .put("items", items)
                    .put("count", items.length())
                    .toString());
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            BookmarkApplicationService.getInstance().clearRecentBookmarks();
            writeJsonResponse(exchange, 200, "{\"status\":\"ok\"}");
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET,DELETE");
        writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void handleBookmarks(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            Integer offset = parseNonNegativeInt(getParam(exchange, "offset"), 0);
            Integer limit = parsePositiveInt(getParam(exchange, "limit"), 100);
            if (offset == null || limit == null || limit > 200) {
                writeJsonResponse(exchange, 400, "{\"error\":\"offset must be non-negative and limit must be 1-200\"}");
                return;
            }
            JSONArray items = new JSONArray();
            List<Bookmark> bookmarks = BookmarkApplicationService.getInstance().listBookmarks(
                    getParam(exchange, "categoryId"), offset, limit + 1);
            boolean hasMore = bookmarks.size() > limit;
            bookmarks.stream().limit(limit).map(DesktopApiV1Dtos::bookmark).forEach(items::put);
            JSONObject response = new JSONObject()
                    .put("items", items)
                    .put("offset", offset)
                    .put("limit", limit)
                    .put("hasMore", hasMore);
            generateJsonResponse(exchange, response.toString());
            return;
        }
        if ("POST".equalsIgnoreCase(method)) {
            JSONObject body;
            try {
                String rawBody = com.uiptv.util.ServerUtils.readRequestBodyText(exchange);
                body = new JSONObject(rawBody);
            } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
                writeJsonResponse(exchange, 413, "{\"error\":\"Request body is too large\"}");
                return;
            } catch (org.json.JSONException _) {
                writeJsonResponse(exchange, 400, "{\"error\":\"A valid JSON request body is required\"}");
                return;
            }
            String accountId = body.optString("accountId", "").trim();
            String categoryDbId = body.optString("categoryId", "").trim();
            String channelDbId = body.optString("channelId", "").trim();
            if (accountId.isEmpty() || categoryDbId.isEmpty() || channelDbId.isEmpty()) {
                writeJsonResponse(exchange, 400,
                        "{\"error\":\"accountId, categoryId, and channelId are required\"}");
                return;
            }
            com.uiptv.application.BookmarkSaveResult saved =
                    BookmarkApplicationService.getInstance().saveCachedLiveChannel(
                            accountId, categoryDbId, channelDbId);
            if (saved == null) {
                writeJsonResponse(exchange, 404,
                        "{\"error\":\"Cached live channel not found for this account and category\"}");
                return;
            }
            writeJsonResponse(exchange, 200, new JSONObject()
                    .put("status", saved.action())
                    .put("bookmarkId", saved.bookmarkId()).toString());
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            String bookmarkId = getParam(exchange, "bookmarkId");
            if (bookmarkId == null || bookmarkId.isBlank()) {
                writeJsonResponse(exchange, 400, "{\"error\":\"bookmarkId is required\"}");
                return;
            }
            if (BookmarkApplicationService.getInstance().getBookmark(bookmarkId) == null) {
                writeJsonResponse(exchange, 404, "{\"error\":\"Favourite not found\"}");
                return;
            }
            BookmarkApplicationService.getInstance().deleteBookmark(bookmarkId);
            writeJsonResponse(exchange, 200, "{\"status\":\"ok\"}");
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET,POST,DELETE");
        writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void handleWatchingNow(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        Integer offset = parseNonNegativeInt(getParam(exchange, "offset"), 0);
        Integer limit = parsePositiveInt(getParam(exchange, "limit"), 100);
        if (offset == null || limit == null || limit > 200) {
            writeJsonResponse(exchange, 400, "{\"error\":\"offset must be non-negative and limit must be 1-200\"}");
            return;
        }
        List<WatchingNowSeriesRow> series = WatchingNowApplicationService.getInstance().listSeriesRows();
        List<WatchingNowVodRow> vod = WatchingNowApplicationService.getInstance().listVodRows();
        JSONArray seriesItems = new JSONArray();
        JSONArray vodItems = new JSONArray();
        series.stream().skip(offset).limit(limit).forEach(row -> seriesItems.put(
                DesktopApiV1Dtos.watchingNowSeries(
                        row, DesktopWatchingNowPlaybackStore.getInstance().register(row))));
        vod.stream().skip(offset).limit(limit).forEach(row -> vodItems.put(
                DesktopApiV1Dtos.watchingNowVod(
                        row, DesktopWatchingNowPlaybackStore.getInstance().register(row))));
        JSONObject response = new JSONObject()
                .put("series", seriesItems)
                .put("vod", vodItems)
                .put("offset", offset)
                .put("limit", limit)
                .put("hasMoreSeries", series.size() > (long) offset + limit)
                .put("hasMoreVod", vod.size() > (long) offset + limit);
        generateJsonResponse(exchange, response.toString());
    }

    private void handleWatchingNowEpisodes(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        String playbackId = getParam(exchange, "playbackId");
        Integer offset = parseNonNegativeInt(getParam(exchange, "offset"), 0);
        Integer limit = parsePositiveInt(getParam(exchange, "limit"), 100);
        if (playbackId == null || playbackId.isBlank() || offset == null || limit == null || limit > 200) {
            writeJsonResponse(exchange, 400,
                    "{\"error\":\"playbackId and bounded pagination are required\"}");
            return;
        }
        DesktopWatchingNowPlaybackStore store = DesktopWatchingNowPlaybackStore.getInstance();
        DesktopWatchingNowPlaybackStore.Selection series = store.resolve(playbackId);
        if (series == null || !"series".equals(series.mode())) {
            writeJsonResponse(exchange, 404, "{\"error\":\"Watching series not found or expired\"}");
            return;
        }

        com.uiptv.model.Account account = WatchingNowApplicationService.getInstance()
                .getAccount(series.accountId());
        if (account == null) {
            writeJsonResponse(exchange, 404, "{\"error\":\"Watching account not found\"}");
            return;
        }
        account.setAction(com.uiptv.model.Account.AccountAction.series);
        List<Channel> episodes = WatchingNowApplicationService.getInstance()
                .listSeriesEpisodes(series.accountId(), series.categoryId(), series.seriesId());
        JSONArray items = new JSONArray();
        episodes.stream().skip(offset).limit(limit).forEach(episode -> items.put(
                DesktopApiV1Dtos.watchingNowEpisode(
                        episode, store.registerEpisode(series, episode))));
        generateJsonResponse(exchange, new JSONObject()
                .put("items", items)
                .put("offset", offset)
                .put("limit", limit)
                .put("nextOffset", offset + items.length())
                .put("hasMore", episodes.size() > (long) offset + limit)
                .toString());
    }

    private void handleAccounts(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        JSONArray response = new JSONArray();
        for (AccountResolver.AccountRow account : AccountApplicationService.getInstance().listAccounts()) {
            JSONObject item = new JSONObject();
            item.put("dbId", account.getDbId());
            item.put("accountName", account.getAccountName());
            item.put("type", account.getType() == null ? "" : account.getType());
            item.put("pinToTop", account.isPinToTop());
            item.put("webPlaybackProxy", account.isWebPlaybackProxy());
            response.put(item);
        }
        writeJsonResponse(exchange, 200, response.toString());
    }
}
