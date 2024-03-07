package com.uiptv.util;

import com.uiptv.service.AccountService;

import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.EMPTY;
import static com.uiptv.util.StringUtils.isBlank;

public class UiptUtils {
    private static final String MAC_ADDRESS_REGEX = "^([0-9A-Fa-f]{2}[:-])"
            + "{5}([0-9A-Fa-f]{2})|"
            + "([0-9a-fA-F]{4}\\."
            + "[0-9a-fA-F]{4}\\."
            + "[0-9a-fA-F]{4})$";

    public static final String SPACER = " ";
    public static String replaceAllNonPrintableChars(String line) {
        try {
            return isBlank(line) ? EMPTY : line.replaceAll("\\p{Cntrl}", SPACER).replaceAll("[^\\p{Print}]", SPACER).replaceAll("\\p{C}", SPACER);
        } catch (Exception ignored) {
        }
        return line;
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

    public static String getUniqueNameFromUrl(String urlString) {
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

    public static String getNameFromUrl(String urlString) {
        try {
            return new URL(urlString).toURI().getHost();
        } catch (Exception ignored) {
        }
        return urlString;
    }

}
