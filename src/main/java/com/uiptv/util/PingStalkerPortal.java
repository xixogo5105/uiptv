package com.uiptv.util;

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

    public static String ping(final String url) {
        try {
            String pingUrl = !url.endsWith("/") ? url + "/" + "xpcom.common.js" : url + "xpcom.common.js";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pingUrl))
                    .GET().build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            //httpLog(url,request, response);
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                return parsePortalApiServer((response.body() + " ").replace(" ", ""), url);
            }
        } catch (Exception ex) {
            System.out.print("Network Error: " + ex.getMessage());
        }
        return PORTAL.getLoader();
    }

    public static String parsePortalApiServer(String jsFileContents, String url) {
        //this.ajax_loader=this.portal_protocol+'://'+this.portal_ip+'/portal.php';
        String portal_api_server = "/c/portal.php";
        try {
            String serverUri = prepareServerUrl(jsFileContents, url);
            if (isNotBlank(serverUri)) {
                return serverUri;
            }
            String portal = "portal.php";
            String load = "load.php";
            if (jsFileContents.toLowerCase().contains(portal)) {
                int i = jsFileContents.toLowerCase().indexOf(portal);
                String delimiter = jsFileContents.toLowerCase().split(portal)[1].substring(0, 1);
                int startingIndex = jsFileContents.toLowerCase().split(portal)[0].lastIndexOf(delimiter + "/");
                portal_api_server = url + jsFileContents.substring(startingIndex + 1, i + (portal.length()));
            } else if (jsFileContents.toLowerCase().contains(load)) {
                int i = jsFileContents.toLowerCase().indexOf(load);
                String delimiter = jsFileContents.toLowerCase().split(load)[1].substring(0, 1);
                int startingIndex = jsFileContents.toLowerCase().split(load)[0].lastIndexOf(delimiter + "/");
                portal_api_server = url + jsFileContents.substring(startingIndex + 1, i + (load.length()));
            }
        } catch (Exception ignored) {
            showError("Parse Error", ignored);
        }
        return portal_api_server;
    }

    private static String prepareServerUrl(String jsFileContents, String uri) {
        //this.ajax_loader=this.portal_protocol+'://'+this.portal_ip+'/portal.php';
        String pattern = "this.ajax_loader=";
        String endsWithString = ";";
        try {
            String[] functionSplitArray = jsFileContents.split(SPLIT_FUNCTION_SERVER_PARAMS);
            if (functionSplitArray.length > 1) {
                String[] patternArray = functionSplitArray[1].split(pattern);
                while (patternArray.length > 1) {
                    //making sure it's not commented out
                    if (patternArray[0].endsWith("//") || patternArray[0].endsWith("/*") || patternArray[0].endsWith("*")) {
                        patternArray = patternArray[1].split(pattern);
                    } else break;
                }
                if (patternArray.length > 1 && patternArray[1].contains(endsWithString)) {
                    String regex = getPattern(jsFileContents).replaceAll("^/+", "").replaceAll("/+$", "");
                    return patternArray[1].substring(0, patternArray[1].indexOf(endsWithString))
                            .replace("'", "")
                            .replace("+", "")
                            .replace(" ", "")
                            .replace(";", "")
                            .replace("this.portal_protocol", uri.replaceFirst(regex, getPortalProtocolParamNumber(jsFileContents, uri)))
                            .replace("this.portal_ip", uri.replaceFirst(regex, getPortalIP(jsFileContents, uri)))
                            .replace("this.portal_port", isBlank(getPortalPort(jsFileContents, uri)) ? "" : uri.replaceFirst(regex, getPortalPort(jsFileContents, uri)))
                            .replace("this.portal_path", uri.replaceFirst(regex, getPortalPath(jsFileContents, uri)));
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String getPattern(String jsFileContents) {
        //this.ajax_loader=this.portal_protocol+'://'+this.portal_ip+'/portal.php';
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
        //this.ajax_loader=this.portal_protocol+'://'+this.portal_ip+'/portal.php';
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
        //this.ajax_loader=this.portal_protocol+'://'+this.portal_ip+'/portal.php';
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
        //this.ajax_loader=this.portal_protocol+'://'+this.portal_ip+'/portal.php';
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
        //this.ajax_loader=this.portal_protocol+'://'+this.portal_ip+'/portal.php';
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


}
