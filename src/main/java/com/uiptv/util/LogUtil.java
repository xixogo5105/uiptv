package com.uiptv.util;

import com.uiptv.ui.LogDisplayUI;

import java.util.Map;

import static com.uiptv.util.StringUtils.EMPTY;

public class LogUtil {
    public static void httpLog(String url, HttpUtil.HttpResult response, Map<String, String> params) {
        com.uiptv.util.AppLog.addLog("URL: " + url);
        com.uiptv.util.AppLog.addLog("==========================================Request Headers==========================================");
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog(String.valueOf(response.requestHeaders()));
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog("==========================================End Request Headers==========================================");
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog("==========================================Request Body==========================================");
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog(params.toString());
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog("==========================================End Request Body==========================================");
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog("==========================================Response Headers==========================================");
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog(String.valueOf(response.responseHeaders()));
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog("==========================================End Response Headers==========================================");
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog("==========================================Response Body==========================================");
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog(response.body());
        com.uiptv.util.AppLog.addLog(EMPTY);
        com.uiptv.util.AppLog.addLog("==========================================End Response Body==========================================");
        com.uiptv.util.AppLog.addLog(EMPTY);
    }
}
