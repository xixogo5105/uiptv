package com.uiptv.service

fun interface VodWatchStateChangeListener {
    fun onChanged(accountId: String, vodId: String)
}
