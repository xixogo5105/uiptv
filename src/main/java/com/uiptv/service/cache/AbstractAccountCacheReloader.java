package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.ui.RssParser;
import com.uiptv.util.AccountType;
import com.uiptv.util.M3U8Parser;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.M3U8Parser.parseChannelPathM3U8;
import static com.uiptv.util.M3U8Parser.parseChannelUrlM3U8;
import static com.uiptv.util.StringUtils.isBlank;

abstract class AbstractAccountCacheReloader implements AccountCacheReloader {
    protected static final String UNCATEGORIZED_ID = "uncategorized";
    protected static final String UNCATEGORIZED_NAME = "Uncategorized";

    protected void clearCache(Account account) {
        ConfigurationService.getInstance().clearCache(account);
    }

    protected void log(LoggerCallback logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

    protected static Map<String, String> getCategoryParams(Account.AccountAction accountAction) {
        final Map<String, String> params = new HashMap<>();
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        params.put("type", accountAction.name());
        params.put("action", accountAction == itv ? "get_genres" : "get_categories");
        return params;
    }

    protected static Map<String, String> getAllChannelsParams(Integer page, Integer perPage) {
        final Map<String, String> params = new HashMap<>();
        params.put("type", "itv");
        params.put("action", "get_all_channels");
        if (page != null) {
            params.put("p", String.valueOf(page));
        }
        if (perPage != null) {
            params.put("per_page", String.valueOf(perPage));
        }
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        return params;
    }

    protected void cacheVodAndSeriesCategoriesOnly(Account account, LoggerCallback logger) {
        if (account.getType() != AccountType.STALKER_PORTAL && account.getType() != AccountType.XTREME_API) {
            return;
        }
        Account.AccountAction original = account.getAction();
        try {
            for (Account.AccountAction mode : List.of(vod, series)) {
                account.setAction(mode);
                List<Category> categories = CategoryService.getInstance().get(account, false, logger);
                saveVodOrSeriesCategories(account, categories);
            }
        } finally {
            account.setAction(original);
        }
    }

    protected void saveVodOrSeriesCategories(Account account, List<Category> categories) {
        if (account.getAction() == vod) {
            VodCategoryDb.get().saveAll(categories, account);
        } else if (account.getAction() == series) {
            SeriesCategoryDb.get().saveAll(categories, account);
        }
    }

    protected List<Channel> m3u8Channels(String category, Account account) throws MalformedURLException {
        Set<Channel> channels = new LinkedHashSet<>();

        Set<PlaylistEntry> m3uCategories = account.getType() == AccountType.M3U8_URL
                ? M3U8Parser.parseUrlCategory(new URL(account.getM3u8Path()))
                : M3U8Parser.parsePathCategory(account.getM3u8Path());
        boolean hasOtherCategories = m3uCategories.size() >= 2;

        List<PlaylistEntry> m3uEntries = account.getType() == AccountType.M3U8_URL
                ? parseChannelUrlM3U8(new URL(account.getM3u8Path()))
                : parseChannelPathM3U8(account.getM3u8Path());

        m3uEntries.stream().filter(e -> {
            String gt = e.getGroupTitle();
            String gtTrim = gt == null ? "" : gt.trim();

            if (category.equalsIgnoreCase("All")) {
                return true;
            }

            if (category.equalsIgnoreCase("Uncategorized")) {
                if (!hasOtherCategories) {
                    return false;
                }
                return gtTrim.isEmpty() || gtTrim.equalsIgnoreCase("Uncategorized");
            }

            return gtTrim.equalsIgnoreCase(category) || (e.getId() != null && e.getId().equalsIgnoreCase(category));
        }).forEach(entry -> {
            String channelId = entry.getId();
            if (isBlank(channelId)) {
                channelId = UUID.nameUUIDFromBytes((entry.getTitle() + entry.getPlaylistEntry()).getBytes()).toString();
            }
            Channel c = new Channel(channelId, entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null, entry.getLogo(), 0, 0, 0, entry.getDrmType(), entry.getDrmLicenseUrl(), entry.getClearKeys(), entry.getInputstreamaddon(), entry.getManifestType());
            channels.add(c);
        });

        return channels.stream().toList();
    }

    protected List<Channel> rssChannels(String category, Account account) throws MalformedURLException {
        Set<Channel> channels = new LinkedHashSet<>();
        List<PlaylistEntry> rssEntries = RssParser.parse(account.getM3u8Path());
        rssEntries.stream().filter(e -> category.equalsIgnoreCase("All") || e.getGroupTitle().equalsIgnoreCase(category) || e.getId().equalsIgnoreCase(category)).forEach(entry -> {
            Channel c = new Channel(entry.getId(), entry.getTitle(), null, entry.getPlaylistEntry(), null, null, null, entry.getLogo(), 0, 0, 0, entry.getDrmType(), entry.getDrmLicenseUrl(), entry.getClearKeys(), entry.getInputstreamaddon(), entry.getManifestType());
            channels.add(c);
        });
        return channels.stream().toList();
    }
}
