package com.uiptv.util

object LogUtil {
    @JvmStatic
    fun httpLog(url: String, response: HttpUtil.HttpResult, params: Map<String, String>) {
        AppLog.addInfoLog(LogUtil::class.java, HttpUtil.formatHttpLog(url, response, params))
    }
}
