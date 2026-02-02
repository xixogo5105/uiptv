package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.uiptv.util.UiptUtils.*;

public class StalkerPortalTextParserService {

    public static final String MODE_STALKER = "Stalker Portal";
    public static final String MODE_XTREME = "Xtreme";
    public static final String MODE_M3U = "M3U Playlists";

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");
    private static final Pattern MAC_PATTERN = Pattern.compile("(?i)(([0-9A-F]{2}[:-]){5}([0-9A-F]{2}))");
    
    private static final Pattern LABELED_USER = Pattern.compile("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*(\\S+)");
    private static final Pattern LABELED_PASS = Pattern.compile("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*(\\S+)");

    public static void saveBulkAccounts(String text, String mode, boolean pauseCaching, boolean groupAccountsByMac, boolean convertM3uToXtreme) {
        if (MODE_M3U.equals(mode)) {
            parseM3uPlaylist(text, pauseCaching, convertM3uToXtreme);
        } else if (MODE_XTREME.equals(mode)) {
            parseXtremeAccounts(text, pauseCaching);
        } else {
            parseStalkerPortal(text, pauseCaching, groupAccountsByMac);
        }
    }

    private static void parseM3uPlaylist(String text, boolean pauseCaching, boolean findXtremeAccountsPlaylist) {
        for (String line : text.split("\\R")) {
            for (final String potentialUrl : replaceAllNonPrintableChars(line).split(SPACER)) {
                if (!isValidURL(potentialUrl)) continue;

                String m3uPlayLIstUrl = potentialUrl;
                String username = null;
                String password = null;
                AccountType accountType = AccountType.M3U8_URL;

                if (findXtremeAccountsPlaylist && isUrlValidXtremeLink(potentialUrl)) {
                    accountType = AccountType.XTREME_API;
                    username = getUserNameFromUrl(potentialUrl);
                    password = getPasswordNameFromUrl(potentialUrl);
                    m3uPlayLIstUrl = potentialUrl.split("get.php?")[0];
                }

                String uniqueName = getUniqueNameFromUrl(m3uPlayLIstUrl);
                AccountService.getInstance().save(new Account(uniqueName, username, password, m3uPlayLIstUrl, null, null, null, null, null, null,
                        accountType, null, m3uPlayLIstUrl, pauseCaching, false));
            }
        }
    }

    private static void parseXtremeAccounts(String text, boolean pauseCaching) {
        List<String> lines = Arrays.asList(text.split("\\R"));
        List<String> currentBlock = new ArrayList<>();

        for (String line : lines) {
            String trimmed = replaceAllNonPrintableChars(line).trim();
            if (trimmed.isEmpty()) continue;

            Matcher urlMatcher = URL_PATTERN.matcher(trimmed);
            if (urlMatcher.find()) {
                if (!currentBlock.isEmpty()) {
                    processXtremeBlock(currentBlock, pauseCaching);
                    currentBlock.clear();
                }
            }
            currentBlock.add(trimmed);
        }
        if (!currentBlock.isEmpty()) {
            processXtremeBlock(currentBlock, pauseCaching);
        }
    }

    private static void processXtremeBlock(List<String> block, boolean pauseCaching) {
        String joinedBlock = String.join(" ", block);
        String url = null, username = null, password = null;

        Matcher mUrl = URL_PATTERN.matcher(joinedBlock);
        if (mUrl.find()) url = mUrl.group(1);
        if (url == null) return;

        Matcher mUser = LABELED_USER.matcher(joinedBlock);
        if (mUser.find()) username = mUser.group(2);
        
        Matcher mPass = LABELED_PASS.matcher(joinedBlock);
        if (mPass.find()) password = mPass.group(3);

        if (username == null || password == null) {
            String remaining = joinedBlock.replace(url, "");
            if (username != null) remaining = remaining.replaceAll("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*" + Pattern.quote(username), "");
            if (password != null) remaining = remaining.replaceAll("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*" + Pattern.quote(password), "");

            String[] tokens = remaining.trim().split("\\s+");
            List<String> unlabeled = Arrays.stream(tokens).filter(s -> !s.isEmpty() && s.length() > 1).collect(Collectors.toList());

            if (username == null && !unlabeled.isEmpty()) username = unlabeled.remove(0);
            if (password == null && !unlabeled.isEmpty()) password = unlabeled.remove(0);
        }

        if (url != null && username != null && password != null) {
            String name = getUniqueNameFromUrl(url);
            AccountService.getInstance().save(new Account(name, username, password, url, null, null, null, null, null, null,
                    AccountType.XTREME_API, null, url, pauseCaching, false));
        }
    }

    private static void parseStalkerPortal(String text, boolean pauseCaching, boolean groupAccountsByMac) {
        List<String> lines = Arrays.asList(text.split("\\R"));
        String currentUrl = null;
        String pendingMac = null;
        String serial = null, deviceId1 = null, deviceId2 = null, signature = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher urlMatcher = URL_PATTERN.matcher(trimmed);
            if (urlMatcher.find()) {
                if (currentUrl != null && pendingMac != null) {
                    saveStalkerAccount(currentUrl, pendingMac, serial, deviceId1, deviceId2, signature, pauseCaching, groupAccountsByMac);
                }
                currentUrl = urlMatcher.group(1);
                pendingMac = null;
                serial = null; deviceId1 = null; deviceId2 = null; signature = null;
                continue;
            }

            Matcher macMatcher = MAC_PATTERN.matcher(trimmed);
            if (macMatcher.find()) {
                if (currentUrl != null && pendingMac != null) {
                    saveStalkerAccount(currentUrl, pendingMac, serial, deviceId1, deviceId2, signature, pauseCaching, groupAccountsByMac);
                    serial = null; deviceId1 = null; deviceId2 = null; signature = null;
                }
                pendingMac = macMatcher.group(1);
                continue;
            }

            String lower = trimmed.toLowerCase();
            if (lower.contains("serial")) serial = getValue(trimmed);
            else if (lower.contains("id1") || (lower.contains("device") && lower.contains("1"))) deviceId1 = getValue(trimmed);
            else if (lower.contains("id2") || (lower.contains("device") && lower.contains("2"))) deviceId2 = getValue(trimmed);
            else if (lower.contains("signature")) signature = getValue(trimmed);
        }

        if (currentUrl != null && pendingMac != null) {
            saveStalkerAccount(currentUrl, pendingMac, serial, deviceId1, deviceId2, signature, pauseCaching, groupAccountsByMac);
        }
    }
    
    private static String getValue(String line) {
        String[] parts = line.split("[:=➤➨]", 2);
        return (parts.length > 1) ? parts[1].trim() : parts[0].trim();
    }

    private static void saveStalkerAccount(String url, String mac, String serial, String deviceId1, String deviceId2, String signature, boolean pauseCaching, boolean collateAccounts) {
        if (url == null || mac == null) return;

        String name = getNameFromUrl(url.replace("_", ""));
        Account account = AccountService.getInstance().getByName(name);

        boolean hasExtraParams = serial != null || deviceId1 != null || deviceId2 != null || signature != null;
        boolean accountCanBeGrouped = collateAccounts && account != null && !hasExtraParams;

        if (accountCanBeGrouped) {
            String currentList = account.getMacAddressList();
            if (currentList == null) currentList = account.getMacAddress();
            
            if (currentList != null && !currentList.contains(mac)) {
                 String macList = currentList + "," + mac;
                 AccountService.getInstance().save(new Account(name, account.getUsername(), account.getPassword(), url, account.getMacAddress(), macList, account.getSerialNumber(), account.getDeviceId1(), account.getDeviceId2(), account.getSignature(),
                    AccountType.STALKER_PORTAL, account.getEpg(), account.getM3u8Path(), pauseCaching, account.isPinToTop()));
            }
        } else {
            String uniqueName = (collateAccounts && !hasExtraParams) ? name : getUniqueNameFromUrl(url);
            AccountService.getInstance().save(new Account(uniqueName, null, null, url, mac, mac, serial, deviceId1, deviceId2, signature,
                    AccountType.STALKER_PORTAL, null, null, pauseCaching, false));
        }
    }
}
