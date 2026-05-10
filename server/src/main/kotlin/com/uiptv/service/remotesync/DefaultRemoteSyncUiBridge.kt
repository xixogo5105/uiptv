package com.uiptv.service.remotesync

import com.uiptv.util.AppLog
import java.util.function.Consumer

class DefaultRemoteSyncUiBridge : RemoteSyncApprovalPrompt, RemoteSyncNotifier {
    override fun requestApproval(request: RemoteSyncApprovalRequest, decisionConsumer: Consumer<Boolean>) {
        decisionConsumer.accept(false)
    }

    override fun showInfo(message: String) {
        AppLog.addInfoLog(DefaultRemoteSyncUiBridge::class.java, message)
    }

    override fun showError(message: String) {
        AppLog.addErrorLog(DefaultRemoteSyncUiBridge::class.java, message)
    }
}
