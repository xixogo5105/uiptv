package com.uiptv.service;

import com.uiptv.db.PublishedM3uSelectionDb;
import com.uiptv.model.Account;
import com.uiptv.model.PublishedM3uSelection;
import com.uiptv.util.AccountType;
import com.uiptv.util.AppLog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class M3U8PublicationService {
    private M3U8PublicationService() {
    }

    public static M3U8PublicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public Set<String> getSelectedAccountIds() {
        return PublishedM3uSelectionDb.get().getAllSelections().stream()
                .map(PublishedM3uSelection::getAccountId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setSelectedAccountIds(Set<String> accountIds) {
        PublishedM3uSelectionDb.get().replaceSelections(accountIds);
    }

    public String getPublishedM3u8() {
        List<Account> accounts = getSelectedAccounts();
        if (accounts.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        result.append("#EXTM3U").append("\n");
        for (Account account : accounts) {
            appendAccountPlaylist(result, account);
        }
        return result.toString();
    }

    private List<Account> getSelectedAccounts() {
        return getSelectedAccountIds().stream()
                .map(AccountService.getInstance()::getById)
                .filter(account -> account != null
                        && (account.getType() == AccountType.M3U8_LOCAL || account.getType() == AccountType.M3U8_URL))
                .toList();
    }

    private void appendAccountPlaylist(StringBuilder result, Account account) {
        if (account == null) {
            return;
        }
        try {
            appendPlaylistLines(result, readPlaylistContent(account));
        } catch (Exception e) {
            AppLog.addErrorLog(M3U8PublicationService.class, "Failed to append playlist for account '" + account.getAccountName() + "'");
            AppLog.addErrorLog(M3U8PublicationService.class, e.getMessage());
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

    @SuppressWarnings("java:S1874")
    private String readUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static class SingletonHelper {
        private static final M3U8PublicationService INSTANCE = new M3U8PublicationService();
    }
}
