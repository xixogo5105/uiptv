package com.uiptv.service.remotesync

import java.util.function.Consumer

fun interface RemoteSyncApprovalPrompt {
    fun requestApproval(request: RemoteSyncApprovalRequest, decisionConsumer: Consumer<Boolean>)
}
