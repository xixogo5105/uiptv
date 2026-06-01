package com.uiptv.ui;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ReloadRunOutcomeTracker {
    private static final Pattern FOUND_CHANNELS_PATTERN = Pattern.compile("Found\\s+Channels\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_RESORT_COLLECTED_PATTERN = Pattern.compile("Collected\\s+(\\d+)\\s+channels", Pattern.CASE_INSENSITIVE);
    private static final Pattern CENSORED_CATEGORIES_PATTERN = Pattern.compile("Censored\\s+Categories\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CENSORED_CHANNELS_PATTERN = Pattern.compile("Censored\\s+Channels\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL_CENSORING_ZERO_RESULT_PATTERN = Pattern.compile(
            "All\\s+(categories|channels)\\s+removed\\s+by\\s+active\\s+censoring\\.\\s+Keeping\\s+existing\\s+cache\\.",
            Pattern.CASE_INSENSITIVE);

    private final Set<String> accountsWithCriticalFailures = ConcurrentHashMap.newKeySet();
    private final Set<String> accountsWithFullCensoringZeroResult = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> fetchedChannelsByAccount = new ConcurrentHashMap<>();
    private final Map<String, Integer> censoredCategoriesByAccount = new ConcurrentHashMap<>();
    private final Map<String, Integer> censoredChannelsByAccount = new ConcurrentHashMap<>();

    void clear() {
        accountsWithCriticalFailures.clear();
        accountsWithFullCensoringZeroResult.clear();
        fetchedChannelsByAccount.clear();
        censoredCategoriesByAccount.clear();
        censoredChannelsByAccount.clear();
    }

    void recordMessage(String accountId, String rawMessage, String compactMessage) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }

        Integer fetched = extractFetchedChannels(rawMessage);
        if (fetched != null && fetched >= 0) {
            fetchedChannelsByAccount.put(accountId, fetched);
        }
        recordCensoredCount(censoredCategoriesByAccount, accountId, extractCount(rawMessage, CENSORED_CATEGORIES_PATTERN));
        recordCensoredCount(censoredChannelsByAccount, accountId, extractCount(rawMessage, CENSORED_CHANNELS_PATTERN));
        if (isFullCensoringZeroResult(rawMessage)) {
            accountsWithFullCensoringZeroResult.add(accountId);
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

    boolean hasFullCensoringZeroResult(String accountId) {
        return accountId != null && accountsWithFullCensoringZeroResult.contains(accountId);
    }

    int getCensoredCategories(String accountId) {
        return censoredCategoriesByAccount.getOrDefault(accountId, 0);
    }

    int getCensoredChannels(String accountId) {
        return censoredChannelsByAccount.getOrDefault(accountId, 0);
    }

    int getTotalCensoredCategories() {
        return censoredCategoriesByAccount.values().stream().mapToInt(Integer::intValue).sum();
    }

    int getTotalCensoredChannels() {
        return censoredChannelsByAccount.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void recordCensoredCount(Map<String, Integer> target, String accountId, Integer count) {
        if (count != null && count > 0) {
            target.merge(accountId, count, Integer::sum);
        }
    }

    private Integer extractFetchedChannels(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }

        Matcher foundChannelsMatcher = FOUND_CHANNELS_PATTERN.matcher(rawMessage);
        if (foundChannelsMatcher.find()) {
            try {
                return Integer.parseInt(foundChannelsMatcher.group(1));
            } catch (NumberFormatException _) {
                return null;
            }
        }

        Matcher lastResortMatcher = LAST_RESORT_COLLECTED_PATTERN.matcher(rawMessage);
        if (lastResortMatcher.find()) {
            try {
                return Integer.parseInt(lastResortMatcher.group(1));
            } catch (NumberFormatException _) {
                return null;
            }
        }

        return null;
    }

    private Integer extractCount(String rawMessage, Pattern pattern) {
        if (rawMessage == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(rawMessage);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private boolean isFullCensoringZeroResult(String rawMessage) {
        return rawMessage != null && FULL_CENSORING_ZERO_RESULT_PATTERN.matcher(rawMessage.trim()).matches();
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
