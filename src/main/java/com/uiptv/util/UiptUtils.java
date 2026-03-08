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
    private UiptUtils() {
    }
    private static final String MAC_ADDRESS_REGEX = "^(?:([0-9A-Fa-f]{2}[:-])"
            + "{5}([0-9A-Fa-f]{2})|"
            + "([0-9a-fA-F]{4}\\."
            + "[0-9a-fA-F]{4}\\."
            + "[0-9a-fA-F]{4}))$";

    public static final String SPACER = " ";

    public static String replaceAllNonPrintableChars(String line) {
        try {
            return isBlank(line) ? EMPTY : line.replaceAll("\\p{Cntrl}", SPACER).replaceAll("[^\\p{Print}]", SPACER).replaceAll("\\p{C}", SPACER);
        } catch (Exception _) {
            // Preserve the original text if sanitization hits an unexpected regex/runtime issue.
        }
        return line;
    }

    public static boolean isValidURL(String urlString) {
        try {
            URL url = new URL(urlString);
            url.toURI();
            return true;
        } catch (Exception _) {
            // Invalid URLs are expected here; report false instead of surfacing parsing failures.
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
        } catch (Exception _) {
            // Fall back to the raw URL when host extraction fails.
        }
        return urlString;
    }

    public static String getNameFromUrl(String urlString) {
        try {
            return new URL(urlString).toURI().getHost();
        } catch (Exception _) {
            // Fall back to the raw URL when host extraction fails.
        }
        return urlString;
    }

    public static boolean isUrlValidXtremeLink(String urlString) {
        try {
            String queryString = new URL(urlString).toURI().getQuery();
            if (isBlank(queryString)) return false;
            return urlString.toLowerCase().contains("get.php?") && queryString.toLowerCase().contains("username") && queryString.toLowerCase().contains("password");
        } catch (Exception _) {
            // Treat malformed playlist links as non-Xtreme URLs.
        }
        return false;
    }

    public static String getPathFromUrl(String urlString) {
        try {
            return urlString.split("get.php?")[0];
        } catch (Exception _) {
            // Preserve the original URL when path extraction fails.
        }
        return urlString;
    }

    public static String getUserNameFromUrl(String urlString) {
        try {
            String queryString = new URL(urlString).toURI().getQuery();
            return getQueryMap(queryString).get("username");
        } catch (Exception _) {
            // Preserve the original URL when username extraction fails.
        }
        return urlString;
    }

    public static String getPasswordNameFromUrl(String urlString) {
        try {
            String queryString = new URL(urlString).toURI().getQuery();
            return getQueryMap(queryString).get("password");
        } catch (Exception _) {
            // Preserve the original URL when password extraction fails.
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

    public static String sanitizeStalkerText(String text) {
        if (isBlank(text)) return EMPTY;
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            sb.appendCodePoint(mapSpecialCodePoint(cp));
        });
        return sb.toString();
    }

    @SuppressWarnings("java:S1479")
    private static int mapSpecialCodePoint(int c) {
        // Mathematical Bold Capital
        if (c >= 0x1D400 && c <= 0x1D419) return 'A' + (c - 0x1D400);
        // Mathematical Bold Small
        if (c >= 0x1D41A && c <= 0x1D433) return 'a' + (c - 0x1D41A);
        // Mathematical Bold Digits
        if (c >= 0x1D7CE && c <= 0x1D7D7) return '0' + (c - 0x1D7CE);
        // Negative Circled Capital
        if (c >= 0x1F150 && c <= 0x1F169) return 'A' + (c - 0x1F150);
        // Negative Squared Capital
        if (c >= 0x1F170 && c <= 0x1F189) return 'A' + (c - 0x1F170);

        switch (c) {
            case 0x029C: return 'H'; // ʜ
            case 0x1D0F: return 'O'; // ᴏ
            case 0x1D1B: return 'T'; // ᴛ
            case 0x1D07: return 'E'; // ᴇ
            case 0x1D00: return 'A'; // ᴀ
            case 0x029F: return 'L'; // ʟ
            case 0x1D18: return 'P'; // ᴘ
            case 0x0280: return 'R'; // ʀ
            case 0x1D1C: return 'U'; // ᴜ
            case 0x0274: return 'N'; // ɴ
            case 0x1D0D: return 'M'; // ᴍ
            case 0x026A: return 'I'; // ɪ
            case 0x1D04: return 'C'; // ᴄ
            case 0x1D20: return 'V'; // ᴠ
            case 0x1D05: return 'D'; // ᴅ
            case 0x2776: return '1'; // ❶
            case 0x2777: return '2'; // ❷
            case 0x278C: return '3'; // ➌
            case 0x279F: return ' '; // ➩
            case 0x27A4: return ' '; // ➤
            case 0x27D0: return ' '; // ⟐
            case 0x1F511: return ' '; // 🔑
            case 0x1F194: return ' '; // 🆔
            case 0x1F4DD: return ' '; // 📝
            case 0x1F538: return ' '; // 🔸
            case 0x25CF: return ' '; // ●
            case 0x251C: return ' '; // ├
            case 0x2500: return ' '; // ─
            case 0x2502: return ' '; // │
            case 0x2570: return ' '; // ╰
            case 0x256D: return ' '; // ╭
            case 0x1F3C1: return ' '; // 🏁
            case 0x23F0: return ' '; // ⏰
            case 0x1F47D: return ' '; // 👽
            case 0x1F510: return ' '; // 🔐
            case 0x1FA62: return ' '; // 🪢
            case 0x1F3AF: return ' '; // 🎯
            case 0x1F4EE: return ' '; // 📮
            case 0x1F310: return ' '; // 🌐
            case 0x26D1: return ' '; // ⛑
            case 0x1F30D: return ' '; // 🌍
            case 0x269C: return ' '; // ⚜
            case 0x2620: return ' '; // ☠
            case 0x2605: return ' '; // ★
            case 0x2606: return ' '; // ☆
            case 0x1F5A5: return ' '; // 🖥
            default: return c;
        }
    }
}
