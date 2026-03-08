package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.util.AccountType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class M3U8PublicationService {
    private final Set<String> selectedAccountIds = new HashSet<>();

    private M3U8PublicationService() {
    }

    private static class SingletonHelper {
        private static final M3U8PublicationService INSTANCE = new M3U8PublicationService();
    }

    public static M3U8PublicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public void setSelectedAccountIds(Set<String> accountIds) {
        this.selectedAccountIds.clear();
        this.selectedAccountIds.addAll(accountIds);
    }

    public Set<String> getSelectedAccountIds() {
        return new HashSet<>(selectedAccountIds);
    }

    public String getPublishedM3u8() {
        if (selectedAccountIds.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        result.append("#EXTM3U").append("\n");
        for (String accountId : selectedAccountIds) {
            appendAccountPlaylist(result, AccountService.getInstance().getById(accountId));
        }
        return result.toString();
    }

    private void appendAccountPlaylist(StringBuilder result, Account account) {
        if (account == null) {
            return;
        }
        try {
            appendPlaylistLines(result, readPlaylistContent(account));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String readPlaylistContent(Account account) throws IOException {
        if (account.getType() == AccountType.M3U8_LOCAL) {
            return readFile(account.getM3u8Path());
        }
        if (account.getType() == AccountType.M3U8_URL) {
            return readUrl(account.getUrl());
        }
        return "";
    }

    private void appendPlaylistLines(StringBuilder result, String content) {
        for (String line : content.split("\\r?\\n")) {
            if (!line.trim().startsWith("#EXTM3U")) {
                result.append(line).append("\n");
            }
        }
    }

    private String readFile(String path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private String readUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
