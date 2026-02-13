package com.uiptv.service;

import com.uiptv.db.CategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Configuration;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.util.AccountType.*;
import static com.uiptv.util.FetchAPI.nullSafeBoolean;
import static com.uiptv.util.FetchAPI.nullSafeInteger;
import static com.uiptv.util.M3U8Parser.parsePathCategory;
import static com.uiptv.util.M3U8Parser.parseUrlCategory;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showError;

public class CategoryService {
    private static CategoryService instance;

    private CategoryService() {
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
        if (account.getType() == RSS_FEED) {
            hardReloadCategories(account);
            List<Category> cats = CategoryDb.get().getCategories(account);
            return censor ? censor(cats) : cats;
        }
        if (NOT_LIVE_TV_CHANNELS.contains(account.getAction())) {
            if (account.getType() == STALKER_PORTAL) {
                HandshakeService.getInstance().hardTokenRefresh(account);
            }
            hardReloadCategories(account);
            List<Category> cats = CategoryDb.get().getCategories(account);
            return censor ? censor(cats) : cats;
        }

        List<Category> cachedCategories = CategoryDb.get().getCategories(account);
        if (cachedCategories.isEmpty() || account.getAction() != itv) {
            hardReloadCategories(account);
            cachedCategories.addAll(CategoryDb.get().getCategories(account));
        } else {
            if (account.getType() == STALKER_PORTAL) {
                HandshakeService.getInstance().hardTokenRefresh(account);
            }

        }
        return censor ? censor(cachedCategories) : cachedCategories;
    }

    private void hardReloadCategories(Account account) {
        List<Category> categories = new ArrayList<>();
        try {
            if (Objects.requireNonNull(account.getType()) == AccountType.M3U8_LOCAL || account.getType() == M3U8_URL) {
                categories.addAll(m3u8Categories(account));
            } else if (account.getType() == AccountType.XTREME_API) {
                categories.addAll(xtremeAPICategories(account));
            } else if (account.getType() == AccountType.RSS_FEED) {
                categories.addAll(rssCategories());
            } else {
                List<Category> s = stalkerPortalCategories(account);
                if (s != null && !s.isEmpty()) categories.addAll(s);
            }
        } catch (Exception e) {
            LogDisplayUI.addLog("Network Error: " + e.getMessage());
        }
        CategoryDb.get().saveAll(categories, account);
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

    private List<Category> stalkerPortalCategories(Account account) {
        HandshakeService.getInstance().connect(account);
        if (account.isNotConnected()) return null;
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
                categoryList.add(new Category(jsonCategory.getString("id"), jsonCategory.getString("title"), jsonCategory.getString("alias"), nullSafeBoolean(jsonCategory, "active_sub"), nullSafeInteger(jsonCategory, "censored")));
            }
        } catch (Exception e) {
            showError("Error while processing response data" + e.getMessage());
        }
        return censor ? censor(categoryList) : categoryList;
    }

    public List<Category> censor(List<Category> categoryList) {
        Configuration configuration = ConfigurationService.getInstance().read();
        String commaSeparatedList = configuration.getFilterCategoriesList();
        if (isBlank(commaSeparatedList) || configuration.isPauseFiltering()) return categoryList;
        List<String> censoredCategories = new ArrayList<>(List.of(ConfigurationService.getInstance().read().getFilterCategoriesList().split(",")));
        censoredCategories.replaceAll(String::trim);
        Predicate<Category> hasCensoredWord = category -> censoredCategories.stream().noneMatch(word -> category.getTitle().toLowerCase().contains(word.toLowerCase()) || category.getCensored() == 1);
        return categoryList.stream().filter(hasCensoredWord).collect(Collectors.toList());
    }

}
