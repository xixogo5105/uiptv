package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.ui.LogDisplayUI;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.uiptv.util.FetchAPI.ServerType.PORTAL;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showError;

public class PingStalkerPortal {

    public static final String SPLIT_FUNCTION_SERVER_PARAMS = "this.get_server_params=function()";

    // Common Stalker API paths to probe
    private static final String[] PROBE_PATHS = {
            "/server/load.php",
            "/portal.php",
            "/c/portal.php",
            "/stalker_portal/server/load.php",
            "/stalker_portal/c/portal.php",
            "/server/portal.php",
            "/mag/c/portal.php"
    };

    // The handshake query string
    private static final String HANDSHAKE_QUERY = "?type=stb&action=handshake&JsHttpRequest=1-xml";

    public static String ping(Account account) {
        final String url = account.getUrl();
        // 1. PHASE ONE: Direct Probing (Hit and Try)
        // We attempt to hit common API endpoints directly with a handshake request.
        String verifiedApi = probeKnownPaths(url, account.getMacAddress());
        if (verifiedApi != null) {
            System.out.println("Found Stalker API via direct probe: " + verifiedApi);
            return verifiedApi;
        }

        // 2. PHASE TWO: XPCOM Parsing (Your Original Logic)
        // If probing fails, we try to download and parse the xpcom.common.js configuration file.
        try {
            String pingUrl = !url.endsWith("/") ? url + "/" + "xpcom.common.js" : url + "xpcom.common.js";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pingUrl))
                    .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                    .GET().build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                return parsePortalApiServer((response.body() + " ").replace(" ", ""), url);
            }
        } catch (Exception ex) {
            // Log but don't stop; we have one final fallback
            LogDisplayUI.addLog("XPCOM Network Error: " + ex.getMessage());
        }

        // 3. PHASE THREE: Hard Fallback
        // Return standard endpoint if all else fails
        return ensureAbsoluteUrl(url) + PORTAL.getLoader();
    }

    /**
     * Iterates through known Stalker paths and attempts a handshake.
     *
     * @param baseUrl    The base URL provided by the user.
     * @param macAddress
     * @return The valid API URL if found, otherwise null.
     */
    private static String probeKnownPaths(String baseUrl, String macAddress) {
        String cleanBase = ensureAbsoluteUrl(baseUrl);

        // Remove trailing slash for cleaner appending
        if (cleanBase.endsWith("/")) {
            cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
        }

        for (String path : PROBE_PATHS) {
            String targetUrl = cleanBase + path;
            if (checkHandshake(targetUrl, macAddress)) {
                return targetUrl;
            }
        }
        return null;
    }

    /**
     * Sends a Stalker handshake request to the given URL.
     * Checks if the response contains the expected JSON token structure.
     */
    private static boolean checkHandshake(String apiUrl, String macAddress) {
        try {
            LogDisplayUI.addLog("Checking handshake for : " + apiUrl);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + HANDSHAKE_QUERY))
                    // Using a set-top box User-Agent often helps avoid blocking
                    .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                    .header("X-User-Agent", "Model: MAG250; Link: WiFi")
                    .header("Referer", apiUrl)
                    .header("Cookie", "mac=" + macAddress + "; stb_lang=en; timezone=GMT")

                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)) // Fast timeout for probing
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                // Valid Stalker handshake response contains "js":{ "token":... } or just "token"
                if (body != null && (body.contains("\"token\"") || body.contains("\"js\""))) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore errors during probing (timeouts, 404s are expected)
        }
        return false;
    }

    public static String parsePortalApiServer(String jsFileContents, String url) {
        // [Existing logic remains unchanged]
        //this.ajax_loader=this.portal_protocol+'://'+this.portal_ip+'/portal.php';
        String portal_api_server = null;
        try {
            String serverUri = prepareServerUrl(jsFileContents, url);
            if (isNotBlank(serverUri) && isValidUrl(serverUri)) {
                return serverUri;
            }
            String portal = "portal.php";
            String load = "load.php";
            if (jsFileContents.toLowerCase().contains(portal)) {
                int i = jsFileContents.toLowerCase().indexOf(portal);
                String delimiter = jsFileContents.toLowerCase().split(portal)[1].substring(0, 1);
                int startingIndex = jsFileContents.toLowerCase().split(portal)[0].lastIndexOf(delimiter + "/");
                String extractedPath = jsFileContents.substring(startingIndex + 1, i + (portal.length()));
                portal_api_server = combineUrlWithPath(url, extractedPath);

                if (!isValidUrl(portal_api_server)) {
                    portal_api_server = null;
                }
            } else if (jsFileContents.toLowerCase().contains(load)) {
                int i = jsFileContents.toLowerCase().indexOf(load);
                String delimiter = jsFileContents.toLowerCase().split(load)[1].substring(0, 1);
                int startingIndex = jsFileContents.toLowerCase().split(load)[0].lastIndexOf(delimiter + "/");
                String extractedPath = jsFileContents.substring(startingIndex + 1, i + (load.length()));
                portal_api_server = combineUrlWithPath(url, extractedPath);

                if (!isValidUrl(portal_api_server)) {
                    portal_api_server = null;
                }
            }
        } catch (Exception ignored) {
            showError("Parse Error", ignored);
        }

        if (isBlank(portal_api_server)) {
            String baseUrl = ensureAbsoluteUrl(url);
            portal_api_server = baseUrl + PORTAL.getLoader();
        }
        return portal_api_server;
    }

    // ... [Rest of your existing private methods: prepareServerUrl, getPattern, etc. remain exactly as they were] ...

    private static String prepareServerUrl(String jsFileContents, String uri) {
        // [Existing implementation]
        String pattern = "this.ajax_loader=";
        String endsWithString = ";";
        try {
            String[] functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS);
            if (functionSplitArray.length > 1) {
                String[] patternArray = functionSplitArray[1].split(pattern);
                while (patternArray.length > 1) {
                    if (patternArray[0].endsWith("//") || patternArray[0].endsWith("/*") || patternArray[0].endsWith("*")) {
                        patternArray = patternArray[1].split(pattern);
                    } else break;
                }
                if (patternArray.length > 1 && patternArray[1].contains(endsWithString)) {
                    String regex = getPattern(jsFileContents).replaceAll("^/+", "").replaceAll("/+$", "");
                    String protocol = getPortalProtocolParamNumber(jsFileContents, uri);
                    String ip = getPortalIP(jsFileContents, uri);
                    String port = getPortalPort(jsFileContents, uri);
                    String path = getPortalPath(jsFileContents, uri);

                    String result = patternArray[1].substring(0, patternArray[1].indexOf(endsWithString))
                            .replace("'", "")
                            .replace("+", "")
                            .replace(" ", "")
                            .replace(";", "")
                            .replace("this.portal_protocol", protocol != null ? uri.replaceFirst(regex, protocol) : "http")
                            .replace("this.portal_ip", ip != null ? uri.replaceFirst(regex, ip) : "")
                            .replace("this.portal_port", port != null && !isBlank(port) ? uri.replaceFirst(regex, port) : "")
                            .replace("this.portal_path", path != null ? uri.replaceFirst(regex, path) : "");

                    if (isNotBlank(result) && !result.contains("://")) {
                        String baseUrl = ensureAbsoluteUrl(uri);
                        if (result.startsWith("/")) {
                            result = baseUrl + result.substring(1);
                        } else {
                            result = baseUrl + result;
                        }
                    }
                    return result;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String getPattern(String jsFileContents) {
        // [Existing implementation]
        String apiServerURL;
        String pattern = "varpattern=";
        String endsWithString = ";";
        try {
            String[] functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS);
            if (functionSplitArray.length > 1) {
                String[] patternArray = functionSplitArray[1].split(pattern);
                if (patternArray.length > 1 && patternArray[1].contains(endsWithString)) {
                    apiServerURL = patternArray[1].substring(0, patternArray[1].indexOf(endsWithString));
                    return apiServerURL;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String getPortalProtocolParamNumber(String jsFileContents, String url) {
        // [Existing implementation]
        String apiServerURL;
        String pattern = "this.portal_protocol=";
        String endsWithString = ";";
        try {
            String[] functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS);
            if (functionSplitArray.length > 1) {
                String[] patternArray = functionSplitArray[1].split(pattern);
                if (patternArray.length > 1 && patternArray[1].contains(endsWithString)) {
                    apiServerURL = patternArray[1].substring(0, patternArray[1].indexOf(endsWithString));
                    return apiServerURL.replace("document.URL.replace(pattern,", "").replace("\"", "").replace(")", "");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String getPortalIP(String jsFileContents, String url) {
        // [Existing implementation]
        String apiServerURL;
        String pattern = "this.portal_ip=";
        String endsWithString = ";";
        try {
            String[] functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS);
            if (functionSplitArray.length > 1) {
                String[] patternArray = functionSplitArray[1].split(pattern);
                if (patternArray.length > 1 && patternArray[1].contains(endsWithString)) {
                    apiServerURL = patternArray[1].substring(0, patternArray[1].indexOf(endsWithString));
                    return apiServerURL.replace("document.URL.replace(pattern,", "").replace("\"", "").replace(")", "");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String getPortalPath(String jsFileContents, String url) {
        // [Existing implementation]
        String apiServerURL;
        String pattern = "this.portal_path=";
        String endsWithString = ";";
        try {
            String[] functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS);
            if (functionSplitArray.length > 1) {
                String[] patternArray = functionSplitArray[1].split(pattern);
                if (patternArray.length > 1 && patternArray[1].contains(endsWithString)) {
                    apiServerURL = patternArray[1].substring(0, patternArray[1].indexOf(endsWithString));
                    return apiServerURL.replace("document.URL.replace(pattern,", "").replace("\"", "").replace(")", "");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String getPortalPort(String jsFileContents, String url) {
        // [Existing implementation]
        String apiServerURL;
        String pattern = "this.portal_port=";
        String endsWithString = ";";
        try {
            String[] functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS);
            if (functionSplitArray.length > 1) {
                String[] patternArray = functionSplitArray[1].split(pattern);
                if (patternArray.length > 1 && patternArray[1].contains(endsWithString)) {
                    apiServerURL = patternArray[1].substring(0, patternArray[1].indexOf(endsWithString));
                    return apiServerURL.replace("document.URL.replace(pattern,", "").replace("\"", "").replace(")", "");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String ensureAbsoluteUrl(String url) {
        // [Existing implementation]
        if (isBlank(url)) {
            return "http://";
        }
        if (url.contains("://")) {
            return url.endsWith("/") ? url : url + "/";
        }
        return "http://" + url;
    }

    private static boolean isValidUrl(String url) {
        // [Existing implementation]
        if (isBlank(url)) {
            return false;
        }
        try {
            URI.create(url);
            return url.contains("://");
        } catch (Exception e) {
            return false;
        }
    }

    private static String combineUrlWithPath(String baseUrl, String path) {
        // [Existing implementation]
        if (isBlank(path)) {
            return baseUrl;
        }
        String base = baseUrl;
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String pathToAppend = path;
        if (pathToAppend.startsWith("/")) {
            pathToAppend = pathToAppend.substring(1);
        }
        return base + pathToAppend;
    }
}