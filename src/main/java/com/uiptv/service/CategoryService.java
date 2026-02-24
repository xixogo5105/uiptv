package com.uiptv.service;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.shared.PlaylistEntry;
import com.uiptv.ui.LogDisplayUI;
import com.uiptv.ui.RssParser;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import com.uiptv.util.ServerUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.util.AccountType.*;
import static com.uiptv.util.FetchAPI.nullSafeBoolean;
import static com.uiptv.util.FetchAPI.nullSafeInteger;
import static com.uiptv.util.M3U8Parser.parsePathCategory;
import static com.uiptv.util.M3U8Parser.parseUrlCategory;
import static com.uiptv.widget.UIptvAlert.showError;

public class CategoryService {
    private static final long VOD_SERIES_CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L;
    private static CategoryService instance;
    private final ContentFilterService contentFilterService;

    private CategoryService() {
        this.contentFilterService = ContentFilterService.getInstance();
    }

    public static synchronized CategoryService getInstance() {
        if (instance == null) {
            instance = new CategoryService();
        }
        return instance;
    }

    private static Map<String, String> getCategoryParams(Account.AccountAction accountAction) {
        final Map<String, String> params = new HashMap<>();
        params.put("JsHttpRequest", new Date().getTime() + "-xml");
        params.put("type", accountAction.name());
        params.put("action", accountAction == itv ? "get_genres" : "get_categories");
        return params;
    }

    public List<Category> get(Account account) {
        return get(account, true);
    }

    public List<Category> get(Account account, boolean censor) {
        return get(account, censor, null);
    }

    public List<Category> get(Account account, boolean censor, LoggerCallback logger) {
        if (account.getType() == RSS_FEED) {
            hardReloadCategories(account, logger);
            List<Category> cats = CategoryDb.get().getCategories(account);
            return maybeFilterCategories(cats, censor);
        }

        if ((account.getAction() == vod || account.getAction() == series)
                && (account.getType() == STALKER_PORTAL || account.getType() == XTREME_API)) {
            List<Category> cached = getVodSeriesCachedCategories(account);
            if (!cached.isEmpty() && isVodSeriesCategoriesFresh(account)) {
                log(logger, "Loaded categories from local cache.");
                return maybeFilterCategories(cached, censor);
            }

            log(logger, "No fresh cached categories found. Fetching from portal/provider...");
            List<Category> fetched = fetchCategoriesFromSource(account);
            if (!fetched.isEmpty()) {
                saveVodSeriesCategories(account, fetched);
                log(logger, "Saved " + fetched.size() + " categories to local VOD/Series cache.");
                List<Category> stored = getVodSeriesCachedCategories(account);
                return maybeFilterCategories(stored.isEmpty() ? fetched : stored, censor);
            }
            return maybeFilterCategories(cached, censor);
        }

        if (NOT_LIVE_TV_CHANNELS.contains(account.getAction())) {
            if (account.getType() == STALKER_PORTAL || account.getType() == XTREME_API) {
                List<Category> cachedCategories = CategoryDb.get().getCategories(account);
                if (!cachedCategories.isEmpty()) {
                    log(logger, "Loaded categories from local cache.");
                    return maybeFilterCategories(cachedCategories, censor);
                }

                log(logger, "No cached categories found. Fetching from portal/provider...");
                List<Category> fetchedCategories = fetchCategoriesFromBackend(account, logger);
                if (!fetchedCategories.isEmpty()) {
                    CategoryDb.get().saveAll(fetchedCategories, account);
                    log(logger, "Saved " + fetchedCategories.size() + " categories to local cache.");
                }
                return maybeFilterCategories(fetchedCategories, censor);
            }

            hardReloadCategories(account, logger);
            List<Category> cats = CategoryDb.get().getCategories(account);
            return maybeFilterCategories(cats, censor);
        }

        List<Category> cachedCategories = CategoryDb.get().getCategories(account);
        if (cachedCategories.isEmpty() || account.getAction() != itv) {
            hardReloadCategories(account, logger);
            cachedCategories.addAll(CategoryDb.get().getCategories(account));
        }
        return maybeFilterCategories(cachedCategories, censor);
    }

    private List<Category> fetchCategoriesFromSource(Account account) {
        List<Category> categories = new ArrayList<>();
        try {
            if (account.getType() == AccountType.XTREME_API) {
                categories.addAll(xtremeAPICategories(account));
            } else {
                List<Category> s = stalkerPortalCategories(account, null);
                if (s != null && !s.isEmpty()) categories.addAll(s);
            }
        } catch (Exception e) {
            LogDisplayUI.addLog("Network Error: " + e.getMessage());
        }
        return categories;
    }

    private List<Category> getVodSeriesCachedCategories(Account account) {
        if (account.getAction() == vod) {
            return VodCategoryDb.get().getCategories(account);
        }
        if (account.getAction() == series) {
            return SeriesCategoryDb.get().getCategories(account);
        }
        return Collections.emptyList();
    }

    private boolean isVodSeriesCategoriesFresh(Account account) {
        if (account.getAction() == vod) {
            return VodCategoryDb.get().isFresh(account, VOD_SERIES_CACHE_TTL_MS);
        }
        if (account.getAction() == series) {
            return SeriesCategoryDb.get().isFresh(account, VOD_SERIES_CACHE_TTL_MS);
        }
        return false;
    }

    private void saveVodSeriesCategories(Account account, List<Category> categories) {
        if (account.getAction() == vod) {
            VodCategoryDb.get().saveAll(categories, account);
        } else if (account.getAction() == series) {
            SeriesCategoryDb.get().saveAll(categories, account);
        }
    }

    private void hardReloadCategories(Account account) {
        hardReloadCategories(account, null);
    }

    private void hardReloadCategories(Account account, LoggerCallback logger) {
        List<Category> categories = fetchCategoriesFromBackend(account, logger);
        CategoryDb.get().saveAll(categories, account);
    }

    private List<Category> fetchCategoriesFromBackend(Account account) {
        return fetchCategoriesFromBackend(account, null);
    }

    private List<Category> fetchCategoriesFromBackend(Account account, LoggerCallback logger) {
        List<Category> categories = new ArrayList<>();
        try {
            if (Objects.requireNonNull(account.getType()) == AccountType.M3U8_LOCAL || account.getType() == M3U8_URL) {
                categories.addAll(m3u8Categories(account));
            } else if (account.getType() == AccountType.XTREME_API) {
                log(logger, "Fetching categories from Xtreme API...");
                categories.addAll(xtremeAPICategories(account));
            } else if (account.getType() == AccountType.RSS_FEED) {
                categories.addAll(rssCategories());
            } else {
                log(logger, "Fetching categories from Stalker Portal...");
                List<Category> s = stalkerPortalCategories(account, logger);
                if (s != null && !s.isEmpty()) categories.addAll(s);
            }
        } catch (Exception e) {
            LogDisplayUI.addLog("Network Error: " + e.getMessage());
            log(logger, "Network error while loading categories: " + e.getMessage());
        }
        return categories;
    }

    private List<Category> xtremeAPICategories(Account account) {
        return XtremeParser.parseCategories(account);
    }

    private List<Category> m3u8Categories(Account account) throws MalformedURLException {
        Set<Category> categories = new LinkedHashSet<>();
        Set<PlaylistEntry> m3uEntries = account.getType() == M3U8_URL ? parseUrlCategory(new URL(account.getM3u8Path())) : parsePathCategory(account.getM3u8Path());
        m3uEntries.forEach(entry -> {
            Category c = new Category(entry.getId(), entry.getGroupTitle(), entry.getGroupTitle(), false, 0);
            categories.add(c);
        });
        return categories.stream().toList();
    }

    private List<Category> rssCategories() {
        Set<Category> categories = new LinkedHashSet<>();
        Set<PlaylistEntry> m3uEntries = RssParser.getCategories();
        m3uEntries.forEach(entry -> {
            Category c = new Category(entry.getId(), entry.getGroupTitle(), entry.getGroupTitle(), false, 0);
            categories.add(c);
        });
        return categories.stream().toList();
    }

    private List<Category> stalkerPortalCategories(Account account, LoggerCallback logger) {
        log(logger, "Performing portal handshake...");
        HandshakeService.getInstance().connect(account);
        if (account.isNotConnected()) {
            log(logger, "Handshake failed.");
            return null;
        }
        log(logger, "Handshake successful. Loading categories...");
        String jsonResponse = FetchAPI.fetch(getCategoryParams(account.getAction()), account);
        return parseCategories(jsonResponse, false);
    }

    public String readToJson(Account account) {
        return ServerUtils.objectToJson(get(account));
    }

    public List<Category> parseCategories(String json, boolean censor) {
        List<Category> categoryList = new ArrayList<>();
        try {
            JSONArray list = new JSONObject(json).getJSONArray("js");
            for (int i = 0; i < list.length(); i++) {
                JSONObject jsonCategory = list.getJSONObject(i);
                Category category = new Category(jsonCategory.getString("id"), jsonCategory.getString("title"), jsonCategory.getString("alias"), nullSafeBoolean(jsonCategory, "active_sub"), nullSafeInteger(jsonCategory, "censored"));
                category.setExtraJson(jsonCategory.toString());
                categoryList.add(category);
            }
        } catch (Exception e) {
            showError("Error while processing response data" + e.getMessage());
        }
        return maybeFilterCategories(categoryList, censor);
    }

    private List<Category> maybeFilterCategories(List<Category> categories, boolean applyFilter) {
        return applyFilter ? contentFilterService.filterCategories(categories) : categories;
    }

    private void log(LoggerCallback logger, String message) {
        if (logger != null) {
            logger.log(message);
        }
    }

}
