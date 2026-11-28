package com.uiptv.server.api.json;

import com.uiptv.model.AccountInfo;
import com.uiptv.model.AccountMediaContext;
import com.uiptv.db.BookmarkPlayHistoryDb;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Account;
import com.uiptv.application.WatchingNowSeriesRow;
import com.uiptv.application.WatchingNowVodRow;
import com.uiptv.service.AccountInfoService;
import com.uiptv.service.AccountResolver;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ChannelService;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

final class DesktopApiV1Dtos {
    private static final DateTimeFormatter COMPACT_EXPIRY_FORMATTER =
            DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH);
    private static final List<String> EXPIRY_DATE_TIME_PATTERNS = List.of(
            "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS", "MMMM d, yyyy, h:mm a", "MMM d, yyyy, h:mm a",
            "MM/dd/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss");
    private static final List<String> EXPIRY_DATE_PATTERNS =
            List.of("yyyy-MM-dd", "yyyy/MM/dd", "MM/dd/yyyy", "dd/MM/yyyy");

    private DesktopApiV1Dtos() {
    }

    static JSONObject category(Category category) {
        return new JSONObject()
                .put("dbId", safe(category.getDbId()))
                .put("title", safe(category.getTitle()))
                .put("alias", safe(category.getAlias()))
                .put("activeSub", category.isActiveSub());
    }

    static JSONObject channel(Channel channel) {
        return new JSONObject()
                .put("dbId", safe(channel.getDbId()))
                .put("name", safe(channel.getName()))
                .put("number", safe(channel.getNumber()))
                .put("logo", safe(channel.getLogo()))
                .put("description", safe(channel.getDescription()))
                .put("watched", channel.isWatched())
                .put("hd", channel.getHd())
                .put("status", channel.getStatus())
                .put("playable", !safe(channel.getCmd()).isBlank());
    }

    static JSONObject channel(Channel channel, String playbackId) {
        return channel(channel, playbackId, "");
    }

    static JSONObject channel(Channel channel, String playbackId, String bookmarkId) {
        JSONObject json = channel(channel)
                .put("bookmarkId", safe(bookmarkId))
                .put("isBookmarked", bookmarkId != null && !bookmarkId.isBlank());
        if (playbackId != null && !playbackId.isBlank()) {
            json.put("playbackId", playbackId);
        }
        return json;
    }

    static JSONObject bookmark(Bookmark bookmark) {
        return new JSONObject()
                .put("dbId", safe(bookmark.getDbId()))
                .put("categoryId", safe(bookmark.getCategoryId()))
                .put("channelName", safe(bookmark.getChannelName()))
                .put("categoryTitle", safe(bookmark.getCategoryTitle()))
                .put("accountName", safe(bookmark.getAccountName()))
                .put("logo", safe(bookmark.getLogo()))
                .put("isBookmarked", !BookmarkPlayHistoryDb.isRecentId(bookmark.getDbId()))
                .put("mode", bookmark.getAccountAction() == null
                        ? "itv"
                        : bookmark.getAccountAction().name().toLowerCase(Locale.ROOT));
    }

    static JSONObject account(AccountResolver.AccountRow account) {
        return new JSONObject()
                .put("dbId", safe(account.getDbId()))
                .put("accountName", safe(account.getAccountName()))
                .put("type", safe(account.getType()))
                .put("pinToTop", account.isPinToTop())
                .put("cacheEnabled", account.isCacheEnabled())
                .put("webPlaybackProxy", account.isWebPlaybackProxy());
    }

    static JSONObject accountSummary(AccountResolver.AccountRow row) {
        JSONObject summary = account(row)
                .put("typeDisplay", accountTypeDisplay(row.getType()))
                .put("pinSvgStemPath", safe(row.getPinSvgStemPath()))
                .put("pinSvgHeadPath", safe(row.getPinSvgHeadPath()))
                .put("pinSvgStemFill", safe(row.getPinSvgStemFill()))
                .put("pinSvgHeadFill", safe(row.getPinSvgHeadFill()))
                .put("pinSvgViewBox", safe(row.getPinSvgViewBox()))
                .put("pinSvgScale", row.getPinSvgScale());
        Account account;
        try {
            account = AccountService.getInstance().getById(row.getDbId());
        } catch (Exception _) {
            account = null;
        }
        int categoryCount = 0;
        int channelCount = 0;
        if (account != null) {
            try {
                categoryCount = cachedCategoryCount(account);
                channelCount = Math.max(0,
                        ChannelService.getInstance().getChannelCountForAccount(row.getDbId()));
            } catch (Exception _) {
                categoryCount = 0;
                channelCount = 0;
            }
        }
        AccountInfo info;
        try {
            info = AccountInfoService.getInstance().getByAccountId(row.getDbId());
        } catch (Exception _) {
            info = null;
        }
        ExpirySummary expiry = accountExpiry(info == null ? "" : info.getExpireDate());
        return summary.put("categoryCount", categoryCount)
                .put("channelCount", channelCount)
                .put("categoryCountDisplay", I18n.formatNumber(String.valueOf(categoryCount)))
                .put("channelCountDisplay", I18n.formatNumber(String.valueOf(channelCount)))
                .put("expiryDate", expiry.date())
                .put("expiryState", expiry.state());
    }

    private static String accountTypeDisplay(String type) {
        try {
            return AccountType.valueOf(safe(type)).getDisplay();
        } catch (IllegalArgumentException _) {
            return safe(type).replace('_', ' ');
        }
    }

    private static int cachedCategoryCount(Account account) {
        int count = cachedCategoryCount(account, Account.AccountAction.itv);
        if (Account.VOD_AND_SERIES_SUPPORTED.contains(account.getType())) {
            count += cachedCategoryCount(account, Account.AccountAction.vod);
            count += cachedCategoryCount(account, Account.AccountAction.series);
        }
        return Math.max(0, count);
    }

    private static int cachedCategoryCount(Account account, Account.AccountAction action) {
        Account modeAccount = AccountMediaContext.from(account, action).toAccount();
        return CategoryService.getInstance().getCached(modeAccount).size();
    }

    private static ExpirySummary accountExpiry(String rawExpiry) {
        String value = safe(rawExpiry).trim();
        if (value.isBlank() || value.startsWith("0000-00-00")) {
            return new ExpirySummary("", "unknown");
        }
        LocalDateTime dateTime = parseExpiryDateTime(value);
        if (dateTime == null) {
            return new ExpirySummary("", "unknown");
        }
        Instant instant = dateTime.atZone(ZoneId.systemDefault()).toInstant();
        long daysRemaining = Duration.between(Instant.now(), instant).toDays();
        String state = daysRemaining < 0 ? "expired" : daysRemaining <= 7 ? "warning" : "ok";
        return new ExpirySummary(COMPACT_EXPIRY_FORMATTER.format(dateTime), state);
    }

    private static LocalDateTime parseExpiryDateTime(String value) {
        String numeric = value.replaceAll("\\D", "");
        if (numeric.length() == 10 || numeric.length() == 13) {
            try {
                long epoch = Long.parseLong(numeric);
                if (numeric.length() == 13) {
                    epoch /= 1000;
                }
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
            } catch (RuntimeException _) {
                return null;
            }
        }
        for (String pattern : EXPIRY_DATE_TIME_PATTERNS) {
            try {
                DateTimeFormatter formatter = pattern.contains("MMMM") || pattern.contains("MMM")
                        ? new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                                .toFormatter(Locale.ENGLISH)
                        : DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException _) {
                // Try the next JavaFX-supported date format.
            }
        }
        for (String pattern : EXPIRY_DATE_PATTERNS) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).atStartOfDay();
            } catch (DateTimeParseException _) {
                // Try the next JavaFX-supported date format.
            }
        }
        return null;
    }

    private record ExpirySummary(String date, String state) {
    }

    static JSONObject accountDetails(Account account) {
        return new JSONObject()
                .put("dbId", safe(account.getDbId()))
                .put("accountName", safe(account.getAccountName()))
                .put("type", account.getType() == null ? "" : account.getType().name())
                .put("username", safe(account.getUsername()))
                .put("password", safe(account.getPassword()))
                .put("url", safe(account.getUrl()))
                .put("macAddress", safe(account.getMacAddress()))
                .put("macAddressList", safe(account.getMacAddressList()))
                .put("serialNumber", safe(account.getSerialNumber()))
                .put("deviceId1", safe(account.getDeviceId1()))
                .put("deviceId2", safe(account.getDeviceId2()))
                .put("signature", safe(account.getSignature()))
                .put("epg", safe(account.getEpg()))
                .put("m3u8Path", safe(account.getM3u8Path()))
                .put("httpMethod", safe(account.getHttpMethod()))
                .put("timezone", safe(account.getTimezone()))
                .put("pinToTop", account.isPinToTop())
                .put("cacheEnabled", account.isCacheEnabled())
                .put("resolveChainAndDeepRedirects", account.isResolveChainAndDeepRedirects())
                .put("webPlaybackProxy", account.isWebPlaybackProxy())
                .put("parentalLock", account.isParentalLock());
    }

    static JSONObject bookmarkCategory(BookmarkCategory category) {
        return new JSONObject()
                .put("id", safe(category.getId()))
                .put("name", safe(category.getName()));
    }

    static JSONObject watchingNowSeries(WatchingNowSeriesRow row) {
        return watchingNowSeries(row, "");
    }

    static JSONObject watchingNowSeries(WatchingNowSeriesRow row, String playbackId) {
        String title = safe(row.seriesTitle());
        if (title.equals(safe(row.seriesId()))) {
            title = "";
        }
        JSONObject json = new JSONObject()
                .put("accountName", safe(row.accountName()))
                .put("seriesTitle", title)
                .put("seriesPoster", safe(row.seriesPoster()))
                .put("episodeName", safe(row.episodeName()))
                .put("season", safe(row.season()))
                .put("episodeNum", row.episodeNum())
                .put("updatedAt", row.updatedAt());
        if (playbackId != null && !playbackId.isBlank()) {
            json.put("playbackId", playbackId);
        }
        return json;
    }

    static JSONObject watchingNowVod(WatchingNowVodRow row) {
        return watchingNowVod(row, "");
    }

    static JSONObject watchingNowVod(WatchingNowVodRow row, String playbackId) {
        String title = safe(row.vodName());
        if (title.equals(safe(row.vodId()))) {
            title = "";
        }
        JSONObject json = new JSONObject()
                .put("accountName", safe(row.accountName()))
                .put("vodName", title)
                .put("vodLogo", safe(row.vodLogo()))
                .put("plot", safe(row.plot()))
                .put("releaseDate", safe(row.releaseDate()))
                .put("rating", safe(row.rating()))
                .put("duration", safe(row.duration()))
                .put("updatedAt", row.updatedAt());
        if (playbackId != null && !playbackId.isBlank()) {
            json.put("playbackId", playbackId);
        }
        return json;
    }

    static JSONObject watchingNowEpisode(Channel episode, String playbackId) {
        return new JSONObject()
                .put("title", safe(episode.getName()))
                .put("season", safe(episode.getSeason()))
                .put("episodeNum", safe(episode.getEpisodeNum()))
                .put("image", safe(episode.getLogo()))
                .put("description", safe(episode.getDescription()))
                .put("rating", safe(episode.getRating()))
                .put("releaseDate", safe(episode.getReleaseDate()))
                .put("playbackId", safe(playbackId));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
