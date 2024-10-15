package com.uiptv.util;

import com.uiptv.service.AccountService;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
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

    public static boolean isUrlValidXtremeLink(String urlString) {
        try {
            String queryString = new URL(urlString).toURI().getQuery();
            if (isBlank(queryString)) return false;
            return urlString.toLowerCase().contains("get.php?") && queryString.toLowerCase().contains("username") && queryString.toLowerCase().contains("password");
        } catch (Exception ignored) {
        }
        return false;
    }

    public static String getPathFromUrl(String urlString) {
        try {
            return urlString.split("get.php?")[0];
        } catch (Exception ignored) {
        }
        return urlString;
    }

    public static String getUserNameFromUrl(String urlString) {
        try {
            String queryString = new URL(urlString).toURI().getQuery();
            return getQueryMap(queryString).get("username");
        } catch (Exception ignored) {
        }
        return urlString;
    }

    public static String getPasswordNameFromUrl(String urlString) {
        try {
            String queryString = new URL(urlString).toURI().getQuery();
            return getQueryMap(queryString).get("password");
        } catch (Exception ignored) {
        }
        return urlString;
    }

    private static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<String, String>();
        for (String param : params) {
            String[] p = param.split("=");
            String name = p[0];
            if (p.length > 1) {
                String value = p[1];
                map.put(name, value);
            }
        }
        return map;
    }
}
