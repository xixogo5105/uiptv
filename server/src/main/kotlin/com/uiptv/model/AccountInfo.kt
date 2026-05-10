package com.uiptv.model

import com.uiptv.shared.BaseJson

data class AccountInfo @JvmOverloads constructor(
    var dbId: String? = null,
    var accountId: String? = null,
    var expireDate: String? = null,
    var accountStatus: AccountStatus? = null,
    var accountBalance: String? = null,
    var tariffName: String? = null,
    var tariffPlan: String? = null,
    var defaultTimezone: String? = null,
    var profileJson: String? = null,
    var passHash: String? = null,
    var parentPasswordHash: String? = null,
    var passwordHash: String? = null,
    var settingsPasswordHash: String? = null,
    var accountPagePasswordHash: String? = null,
    var allowedStbTypesJson: String? = null,
    var allowedStbTypesForLocalRecordingJson: String? = null,
    var preferredStbType: String? = null
) : BaseJson()
