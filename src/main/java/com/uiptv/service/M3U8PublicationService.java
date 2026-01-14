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
    private static M3U8PublicationService instance;
    private final Set<String> selectedAccountIds = new HashSet<>();

    private M3U8PublicationService() {
    }

    public static synchronized M3U8PublicationService getInstance() {
        if (instance == null) {
            instance = new M3U8PublicationService();
        }
        return instance;
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
            Account account = AccountService.getInstance().getById(accountId);
            if (account != null) {
                try {
                    String content = "";
                    if (account.getType() == AccountType.M3U8_LOCAL) {
                        content = readFile(account.getM3u8Path());
                    } else if (account.getType() == AccountType.M3U8_URL) {
                        content = readUrl(account.getUrl());
                    }
                    
                    String[] lines = content.split("\\r?\\n");
                    for (String line : lines) {
                        if (line.trim().startsWith("#EXTM3U")) {
                            continue;
                        }
                        result.append(line).append("\n");
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return result.toString();
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