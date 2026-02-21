package com.uiptv.service;

import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.uiptv.util.StringUtils.isBlank;

public class ContentFilterService {
    private static ContentFilterService instance;

    private ContentFilterService() {
    }

    public static synchronized ContentFilterService getInstance() {
        if (instance == null) {
            instance = new ContentFilterService();
        }
        return instance;
    }

    public List<Channel> filterChannels(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return channels;
        }
        Configuration configuration = ConfigurationService.getInstance().read();
        String filterList = configuration == null ? null : configuration.getFilterChannelsList();

        // Keep existing behavior: no filtering if list is blank or filtering is paused.
        if (isBlank(filterList) || (configuration != null && configuration.isPauseFiltering())) {
            return channels;
        }

        List<String> blockedWords = parseCsv(filterList);
        Predicate<Channel> keepChannel = channel -> {
            if (channel == null) {
                return false;
            }
            String safeName = StringUtils.safeUtf(channel.getName()).toLowerCase();
            boolean containsBlockedWord = blockedWords.stream().anyMatch(word -> safeName.contains(word.toLowerCase()));
            return !containsBlockedWord && channel.getCensored() != 1;
        };

        return channels.stream().filter(keepChannel).collect(Collectors.toList());
    }

    public List<Category> filterCategories(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return categories;
        }
        Configuration configuration = ConfigurationService.getInstance().read();
        String filterList = configuration == null ? null : configuration.getFilterCategoriesList();

        // Keep existing behavior: no filtering if list is blank or filtering is paused.
        if (isBlank(filterList) || (configuration != null && configuration.isPauseFiltering())) {
            return categories;
        }

        List<String> blockedWords = parseCsv(filterList);
        Predicate<Category> keepCategory = category -> {
            if (category == null) {
                return false;
            }
            boolean hasBlockedWord = blockedWords.stream().anyMatch(word ->
                    category.getTitle() != null && category.getTitle().toLowerCase().contains(word.toLowerCase())
            );
            return !hasBlockedWord && category.getCensored() != 1;
        };

        return categories.stream().filter(keepCategory).collect(Collectors.toList());
    }

    private List<String> parseCsv(String csv) {
        List<String> values = new ArrayList<>(List.of(csv.split(",")));
        values.replaceAll(String::trim);
        return values;
    }
}
