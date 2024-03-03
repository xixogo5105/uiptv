package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;

public class StalkerPortalTextParserService {
    private static final String MAC_ADDRESS_REGEX = "^([0-9A-Fa-f]{2}[:-])"
            + "{5}([0-9A-Fa-f]{2})|"
            + "([0-9a-fA-F]{4}\\."
            + "[0-9a-fA-F]{4}\\."
            + "[0-9a-fA-F]{4})$";

    public static void saveBulkAccounts(String text, boolean pauseCachingCheckBox) {
        String spacer = " ";
        String currentUrl = null;
        for (String line : text.split("\\R")) {
            for (String potentialUrlOrMac : line.split(spacer)) {
                if (isValidURL(potentialUrlOrMac)) {
                    currentUrl = potentialUrlOrMac;
                } else if (currentUrl != null && isValidMACAddress(potentialUrlOrMac)) {
                    String name = getNameFromUrl(currentUrl);
                    AccountService.getInstance().save(new Account(name, null, null, currentUrl, potentialUrlOrMac, null, null, null, null,
                            AccountType.STALKER_PORTAL, null, null, pauseCachingCheckBox));
                }
            }
        }
    }

    public static boolean isValidURL(String urlString) {
        try {
            URL url = new URL(urlString);
            url.toURI();
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean isValidMACAddress(String line) {
        if (isBlank(line)) return false;
        return Pattern.compile(MAC_ADDRESS_REGEX).matcher(line).matches();
    }

    public static String getNameFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            URI uri = url.toURI();
            int i = 1;
            String validName;
            do {
                validName = uri.getHost() + " (" + i++ + ")";
            } while (AccountService.getInstance().getByName(validName) != null);
            return validName;
        } catch (Exception ignored) {
        }
        return urlString;
    }
}