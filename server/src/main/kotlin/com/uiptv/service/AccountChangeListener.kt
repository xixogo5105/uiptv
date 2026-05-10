package com.uiptv.service

fun interface AccountChangeListener {
    fun onAccountsChanged(revision: Long)
}
