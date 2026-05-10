package com.uiptv.service.remotesync

interface RemoteSyncNotifier {
    fun showInfo(message: String)
    fun showError(message: String)
}
