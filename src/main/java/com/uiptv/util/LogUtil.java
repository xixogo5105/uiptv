package com.uiptv.util;
import java.util.Map;

public class LogUtil {
    private LogUtil() {
    }

    public static void httpLog(String url, HttpUtil.HttpResult response, Map<String, String> params) {
        com.uiptv.util.AppLog.addLog(HttpUtil.formatHttpLog(url, response, params));
    }
}
