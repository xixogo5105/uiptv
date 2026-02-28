package com.uiptv.ui;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ReloadRunOutcomeTracker {
    private static final Pattern FOUND_CHANNELS_PATTERN = Pattern.compile("Found\\s+Channels\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_RESORT_COLLECTED_PATTERN = Pattern.compile("Collected\\s+(\\d+)\\s+channels", Pattern.CASE_INSENSITIVE);

    private final Set<String> accountsWithCriticalFailures = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> fetchedChannelsByAccount = new ConcurrentHashMap<>();

    void clear() {
        accountsWithCriticalFailures.clear();
        fetchedChannelsByAccount.clear();
    }

    void recordMessage(String accountId, String rawMessage, String compactMessage) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }

        Integer fetched = extractFetchedChannels(rawMessage);
        if (fetched != null && fetched >= 0) {
            fetchedChannelsByAccount.put(accountId, fetched);
        }

        if (isCriticalFailureMessage(rawMessage, compactMessage)) {
            accountsWithCriticalFailures.add(accountId);
        }
    }

    int getFetchedChannels(String accountId) {
        return fetchedChannelsByAccount.getOrDefault(accountId, 0);
    }

    boolean hasCriticalFailure(String accountId) {
        return accountId != null && accountsWithCriticalFailures.contains(accountId);
    }

    private Integer extractFetchedChannels(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }

        Matcher foundChannelsMatcher = FOUND_CHANNELS_PATTERN.matcher(rawMessage);
        if (foundChannelsMatcher.find()) {
            try {
                return Integer.parseInt(foundChannelsMatcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        Matcher lastResortMatcher = LAST_RESORT_COLLECTED_PATTERN.matcher(rawMessage);
        if (lastResortMatcher.find()) {
            try {
                return Integer.parseInt(lastResortMatcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private boolean isCriticalFailureMessage(String rawMessage, String compactMessage) {
        if (compactMessage != null && compactMessage.startsWith("Failed:")) {
            return true;
        }
        if (rawMessage == null) {
            return false;
        }
        String trimmed = rawMessage.trim();
        return trimmed.startsWith("Reload failed:")
                || trimmed.equals("Handshake failed.")
                || trimmed.startsWith("Handshake failed for")
                || trimmed.startsWith("Network error while loading categories")
                || trimmed.startsWith("Failed to parse channels")
                || trimmed.startsWith("Last-resort fetch failed for category");
    }
}
