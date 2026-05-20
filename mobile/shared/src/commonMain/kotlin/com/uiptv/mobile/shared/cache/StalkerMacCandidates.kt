package com.uiptv.mobile.shared.cache

import com.uiptv.mobile.shared.accounts.MobileAccount

internal fun MobileAccount.stalkerMacCandidates(): List<String> =
    (listOf(macAddress) + macAddressList.split(","))
        .map { it.filterNot(Char::isWhitespace) }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

internal fun MobileAccount.withStalkerMac(macAddress: String): MobileAccount =
    if (this.macAddress.equals(macAddress, ignoreCase = true)) {
        this
    } else {
        copy(macAddress = macAddress)
    }
