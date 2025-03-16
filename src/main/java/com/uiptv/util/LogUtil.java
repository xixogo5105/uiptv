package com.uiptv.util;

import com.uiptv.ui.LogDisplayUI;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static com.uiptv.util.StringUtils.EMPTY;

public class LogUtil {
    public static void httpLog(String url, HttpRequest request, HttpResponse<String> response, Map<String, String> params) {
        LogDisplayUI.addLog("URL: " + url);
        LogDisplayUI.addLog("==========================================Request Headers==========================================");
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog(String.valueOf(request.headers()));
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog("==========================================End Request Headers==========================================");
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog("==========================================Request Body==========================================");
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog(params.toString());
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog("==========================================End Request Body==========================================");
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog("==========================================Response Headers==========================================");
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog(String.valueOf(response.headers()));
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog("==========================================End Response Headers==========================================");
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog("==========================================Response Body==========================================");
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog(response.body());
        LogDisplayUI.addLog(EMPTY);
        LogDisplayUI.addLog("==========================================End Response Body==========================================");
        LogDisplayUI.addLog(EMPTY);
    }
}
