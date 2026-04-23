package com.uiptv.util;
import java.util.Map;

public class LogUtil {
    private LogUtil() {
    }

    public static void httpLog(String url, HttpUtil.HttpResult response, Map<String, String> params) {
        com.uiptv.util.AppLog.addInfoLog(LogUtil.class, HttpUtil.formatHttpLog(url, response, params));
    }
}
