package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.uiptv.util.UiptUtils.*;

public class StalkerPortalTextParserService {

    // Regex patterns for robust Xtreme parsing.
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");
    private static final Pattern LABELED_USER = Pattern.compile("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*(\\S+)");
    private static final Pattern LABELED_PASS = Pattern.compile("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*(\\S+)");

    public static void saveBulkAccounts(String text, boolean pauseCaching, boolean collateAccounts, boolean parsePlaylist, boolean findXtremeAccountsPlaylist, boolean parseXtreme) {
        if (parsePlaylist) {
            parseM3uPlaylist(text, pauseCaching, collateAccounts, findXtremeAccountsPlaylist);
        } else if (parseXtreme) {
            parseXtremeAccounts(text, pauseCaching);
        } else {
            parseStalkerPortal(text, pauseCaching, collateAccounts);
        }
    }

    private static void parseM3uPlaylist(String text, boolean pauseCaching, boolean collateAccounts, boolean findXtremeAccountsPlaylist) {
        for (String line : text.split("\\R")) {
            for (final String potentialUrl : replaceAllNonPrintableChars(line).split(SPACER)) {
                String m3uPlayLIstUrl;
                String username = null;
                String password = null;
                if (isValidURL(potentialUrl)) {
                    AccountType accountType = AccountType.M3U8_URL;
                    if (findXtremeAccountsPlaylist && isUrlValidXtremeLink(potentialUrl)) {
                        accountType = AccountType.XTREME_API;
                        username = getUserNameFromUrl(potentialUrl);
                        password = getPasswordNameFromUrl(potentialUrl);
                        m3uPlayLIstUrl = potentialUrl.split("get.php?")[0];
                    } else {
                        m3uPlayLIstUrl = potentialUrl;
                    }
                    String name = getNameFromUrl(m3uPlayLIstUrl);
                    Account account = AccountService.getInstance().getByName(name);
                    boolean accountExist = collateAccounts && account != null && accountType.equals(account.getType());
                    if (accountExist) {
                        AccountService.getInstance().save(new Account(name, username, password, m3uPlayLIstUrl, account.getMacAddress(), account.getMacAddressList(), account.getSerialNumber(), account.getDeviceId1(), account.getDeviceId2(), account.getSignature(),
                                accountType, account.getEpg(), m3uPlayLIstUrl, pauseCaching, account.isPinToTop()));
                    } else {
                        String uniqueName = collateAccounts ? name : getUniqueNameFromUrl(m3uPlayLIstUrl);
                        AccountService.getInstance().save(new Account(uniqueName, username, password, m3uPlayLIstUrl, null, null, null, null, null, null,
                                accountType, null, m3uPlayLIstUrl, pauseCaching, false));
                    }
                }
            }
        }
    }

    private static void parseXtremeAccounts(String text, boolean pauseCaching) {
        List<String> lines = Arrays.asList(text.split("\\R"));
        List<List<String>> blocks = new ArrayList<>();
        List<String> currentBlock = new ArrayList<>();

        for (String line : lines) {
            String trimmed = replaceAllNonPrintableChars(line).trim();
            if (trimmed.isEmpty()) continue;

            Matcher urlMatcher = URL_PATTERN.matcher(trimmed);
            if (urlMatcher.find()) {
                if (!currentBlock.isEmpty()) {
                    blocks.add(new ArrayList<>(currentBlock));
                    currentBlock.clear();
                }
            }
            currentBlock.add(trimmed);
        }
        if (!currentBlock.isEmpty()) {
            blocks.add(currentBlock);
        }

        for (List<String> block : blocks) {
            processXtremeBlock(block, pauseCaching);
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
            List<String> unlabeled = Arrays.stream(tokens).filter(s -> !s.isEmpty()).collect(Collectors.toList());

            if (username == null && !unlabeled.isEmpty()) username = unlabeled.remove(0);
            if (password == null && !unlabeled.isEmpty()) password = unlabeled.remove(0);
        }

        if (url != null && username != null && password != null) {
            saveXtremeAccount(url, username, password, pauseCaching);
        }
    }

    private static void saveXtremeAccount(String url, String username, String password, boolean pauseCaching) {
        String name = getUniqueNameFromUrl(url);
        AccountService.getInstance().save(new Account(name, username, password, url, null, null, null, null, null, null,
                AccountType.XTREME_API, null, url, pauseCaching, false));
    }

    private static void parseStalkerPortal(String text, boolean pauseCaching, boolean collateAccounts) {
        List<String> lines = Arrays.asList(text.split("\\R"));
        List<List<String>> blocks = new ArrayList<>();
        List<String> currentBlock = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String normalizedKey = Normalizer.normalize(trimmed.split("[:➤➨]")[0], Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "").toLowerCase();
            boolean isBlockStart = (normalizedKey.contains("portal") || normalizedKey.contains("real") || normalizedKey.contains("panel")) && URL_PATTERN.matcher(trimmed).find();
            
            if (isBlockStart && !currentBlock.isEmpty()) {
                blocks.add(new ArrayList<>(currentBlock));
                currentBlock.clear();
            }
            currentBlock.add(trimmed);
        }
        if (!currentBlock.isEmpty()) {
            blocks.add(currentBlock);
        }

        for (List<String> block : blocks) {
            processStalkerBlock(block, pauseCaching, collateAccounts);
        }
    }

    private static void processStalkerBlock(List<String> block, boolean pauseCaching, boolean collateAccounts) {
        String url = null, mac = null, serial = null, deviceId1 = null, deviceId2 = null, signature = null;

        for (String line : block) {
            String[] parts = line.split("[:➤➨]", 2);
            String key = parts[0];
            String value = (parts.length > 1) ? parts[1].trim() : key.trim();

            String normalizedKey = Normalizer.normalize(key, Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "").toLowerCase();

            Matcher urlMatcher = URL_PATTERN.matcher(value);
            if (urlMatcher.find()) {
                if (normalizedKey.contains("portal") || normalizedKey.contains("real") || normalizedKey.contains("url") || normalizedKey.isEmpty() || parts.length == 1) {
                    if (!value.contains("get.php")) { // Avoid capturing M3U links as portal URLs
                        url = urlMatcher.group(1);
                    }
                }
            } else if (isValidMACAddress(value)) {
                mac = value;
            } else if (normalizedKey.contains("serial") && !normalizedKey.contains("cut")) {
                serial = value;
            } else if (normalizedKey.contains("cut")) {
                if (serial == null) serial = value;
            } else if (normalizedKey.contains("id1") || (normalizedKey.contains("device") && normalizedKey.contains("1"))) {
                deviceId1 = value;
            } else if (normalizedKey.contains("id2") || (normalizedKey.contains("device") && normalizedKey.contains("2"))) {
                deviceId2 = value;
            } else if (normalizedKey.contains("signature")) {
                signature = value;
            }
        }

        if (url != null && mac != null) {
            saveStalkerAccount(url, mac, serial, deviceId1, deviceId2, signature, pauseCaching, collateAccounts);
        }
    }

    private static void saveStalkerAccount(String url, String mac, String serial, String deviceId1, String deviceId2, String signature, boolean pauseCaching, boolean collateAccounts) {
        if (url == null || mac == null) return;

        String name = getNameFromUrl(url.replace("_", ""));
        Account account = AccountService.getInstance().getByName(name);

        boolean hasExtraParams = serial != null || deviceId1 != null || deviceId2 != null || signature != null;
        boolean accountCanBeGrouped = collateAccounts && account != null && !hasExtraParams;

        if (accountCanBeGrouped) {
            String macList = account.getMacAddressList() + "," + mac;
            AccountService.getInstance().save(new Account(name, account.getUsername(), account.getPassword(), url, account.getMacAddress(), macList, account.getSerialNumber(), account.getDeviceId1(), account.getDeviceId2(), account.getSignature(),
                    AccountType.STALKER_PORTAL, account.getEpg(), account.getM3u8Path(), pauseCaching, account.isPinToTop()));
        } else {
            String uniqueName = (collateAccounts && !hasExtraParams) ? name : getUniqueNameFromUrl(url);
            AccountService.getInstance().save(new Account(uniqueName, null, null, url, mac, mac, serial, deviceId1, deviceId2, signature,
                    AccountType.STALKER_PORTAL, null, null, pauseCaching, false));
        }
    }
}
