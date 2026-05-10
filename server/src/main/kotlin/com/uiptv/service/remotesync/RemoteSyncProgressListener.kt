package com.uiptv.service.remotesync

fun interface RemoteSyncProgressListener {
    fun onProgress(step: RemoteSyncProgressStep, detail: String)
}
