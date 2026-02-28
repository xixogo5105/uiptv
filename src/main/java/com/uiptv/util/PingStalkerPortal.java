package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.ui.LogDisplayUI;
import org.json.JSONObject;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static com.uiptv.util.FetchAPI.ServerType.PORTAL;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showError;

public class PingStalkerPortal {
    private enum ProbeStatus {
        SUCCESS,
        NO_MATCH,
        NETWORK_ERROR
    }

    public static final String SPLIT_FUNCTION_SERVER_PARAMS = "this.get_server_params=function()";

    // Ordered by commonly seen Stalker/Ministra deployments.
    private static final String[] PROBE_PATHS = {
            "/c/portal.php",
            "/stalker_portal/c/portal.php",
            "/portal.php",
            "/server/load.php",
            "/stalker_portal/server/load.php",
            "/server/portal.php",
            "/mag/c/portal.php"
    };

    public static String ping(Account account) {
        final String url = account.getUrl();
        final String timezone = account.getTimezone() != null ? account.getTimezone() : "Europe/London";
        final String httpMethod = account.getHttpMethod() != null ? account.getHttpMethod() : "GET";
        // Try reading and parsing xpcom.common.js first.
        LogDisplayUI.addLog("Attempting to download xpcom.common.js from portal base URL: " + url);
        try {
            String pingUrl = !url.endsWith("/") ? url + "/" + "xpcom.common.js" : url + "xpcom.common.js";

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3");

            HttpUtil.HttpResult response = HttpUtil.sendRequest(pingUrl, headers, httpMethod);

            if (response.statusCode() == HttpUtil.STATUS_OK) {
                String discoveredApi = parsePortalApiServer((response.body() + " ").replace(" ", ""), url);
                String defaultApi = getDefaultApiEndpoint(url);
                if (isNotBlank(discoveredApi) && !discoveredApi.equalsIgnoreCase(defaultApi)) {
                    LogDisplayUI.addLog("Successfully parsed xpcom.common.js and resolved a specific API endpoint: " + discoveredApi);
                    return discoveredApi;
                }
                LogDisplayUI.addLog("xpcom.common.js was accessible, but did not yield a specific API endpoint. Trying known API paths next.");
            } else {
                LogDisplayUI.addLog("Unable to access xpcom.common.js (HTTP " + response.statusCode() + "). Trying known API paths next.");
            }
        } catch (Exception ex) {
            if (isNetworkFailure(ex)) {
                LogDisplayUI.addLog("Network/connection issue while requesting xpcom.common.js: " + rootCauseMessage(ex) + ". Skipping endpoint probing.");
                return getDefaultApiEndpoint(url);
            }
            LogDisplayUI.addLog("Error while requesting xpcom.common.js: " + ex.getMessage() + ". Trying known API paths next.");
        }

        // If xpcom.common.js parsing does not resolve an endpoint, probe known API paths.
        LogDisplayUI.addLog("Probing known Stalker API endpoints.");
        String verifiedApi = probeKnownPaths(url, account.getMacAddress(), timezone, httpMethod);
        if (verifiedApi != null) {
            LogDisplayUI.addLog("Successfully discovered working API endpoint via direct handshake probing: " + verifiedApi);
            return verifiedApi;
        }

        // Return standard endpoint if all discovery attempts fail.
        LogDisplayUI.addLog("No working endpoint discovered from xpcom.common.js or probing. Falling back to default API endpoint.");
        return getDefaultApiEndpoint(url);
    }

    /**
     * Iterates through known Stalker paths and attempts a handshake.
     *
     * @param baseUrl    The base URL provided by the user.
     * @param macAddress The MAC address for the device
     * @param timezone   The timezone from Account
     * @param httpMethod The HTTP method from Account (GET or POST)
     * @return The valid API URL if found, otherwise null.
     */
    private static String probeKnownPaths(String baseUrl, String macAddress, String timezone, String httpMethod) {
        String cleanBase = ensureAbsoluteUrl(baseUrl);

        // Remove trailing slash for cleaner appending
        if (cleanBase.endsWith("/")) {
            cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
        }

        for (String path : PROBE_PATHS) {
            String targetUrl = cleanBase + path;
            ProbeStatus result = checkHandshake(targetUrl, macAddress, timezone, httpMethod);
            if (result == ProbeStatus.SUCCESS) {
                return targetUrl;
            }
            if (result == ProbeStatus.NETWORK_ERROR) {
                LogDisplayUI.addLog("Stopping endpoint probing after connection-level failure at: " + targetUrl);
                return null;
            }
        }
        return null;
    }

    /**
     * Sends a Stalker handshake request to the given URL.
     * Checks if the response contains the expected JSON token structure.
     *
     * @param apiUrl The API endpoint URL
     * @param macAddress The MAC address for the device
     * @param timezone The timezone setting
     * @param httpMethod The HTTP method to use (GET or POST)
     */
    private static ProbeStatus checkHandshake(String apiUrl, String macAddress, String timezone, String httpMethod) {
        try {
            LogDisplayUI.addLog("Checking handshake for : " + apiUrl);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3");
            headers.put("X-User-Agent", "Model: MAG250; Link: WiFi");
            headers.put("Referer", apiUrl);
            headers.put("Cookie", "mac=" + macAddress + "; stb_lang=en; timezone=" + timezone);

            String handshakeQuery = "?type=stb&action=handshake&JsHttpRequest=" + System.currentTimeMillis() + "-xml";
            HttpUtil.HttpResult response = HttpUtil.sendRequest(apiUrl + handshakeQuery, headers, httpMethod);

            if (response.statusCode() == 200) {
                String body = response.body();
                if (hasHandshakeToken(body)) {
                    return ProbeStatus.SUCCESS;
                }
            }
        } catch (Exception e) {
            if (isNetworkFailure(e)) {
                LogDisplayUI.addLog("Network/connection issue while checking " + apiUrl + ": " + rootCauseMessage(e));
                return ProbeStatus.NETWORK_ERROR;
            }
        }
        return ProbeStatus.NO_MATCH;
    }

    private static boolean isNetworkFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof SocketTimeoutException
                    || current instanceof SSLException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("ConnectTimeoutException")
                    || className.contains("ConnectionRequestTimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + (isNotBlank(current.getMessage()) ? (": " + current.getMessage()) : "");
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
            portal_api_server = getDefaultApiEndpoint(url);
        }
        return portal_api_server;
    }

    private static String getDefaultApiEndpoint(String url) {
        String baseUrl = ensureAbsoluteUrl(url);
        String lowered = baseUrl.toLowerCase();
        if (lowered.contains("/c/") && !lowered.endsWith(".php/") && !lowered.endsWith(".php")) {
            int cIndex = lowered.indexOf("/c/");
            String root = baseUrl.substring(0, cIndex + 1);
            return root + "server/load.php";
        }
        return baseUrl + PORTAL.getLoader();
    }

    private static boolean hasHandshakeToken(String body) {
        if (isBlank(body)) {
            return false;
        }
        try {
            JSONObject root = new JSONObject(body);
            JSONObject js = root.optJSONObject("js");
            if (js != null && isNotBlank(js.optString("token"))) {
                return true;
            }
            return isNotBlank(root.optString("token"));
        } catch (Exception ignored) {
            return false;
        }
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
