package com.uiptv.mobile.shared.cache

import com.uiptv.mobile.shared.accounts.MobileAccount
import kotlin.test.Test
import kotlin.test.assertEquals

class StalkerMacCandidatesTest {
    @Test
    fun candidatesIncludePrimaryThenListWithoutDuplicates() {
        val account = MobileAccount(
            macAddress = " 00:11:22:33:44:55 ",
            macAddressList = "AA:BB:CC:DD:EE:FF,00:11:22:33:44:55, aa:bb:cc:dd:ee:ff"
        )

        assertEquals(
            listOf("00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF"),
            account.stalkerMacCandidates()
        )
    }

    @Test
    fun candidatesFallBackToSavedListWhenPrimaryIsBlank() {
        val account = MobileAccount(macAddressList = " 00:AA:BB:CC:DD:EE , ")

        assertEquals(listOf("00:AA:BB:CC:DD:EE"), account.stalkerMacCandidates())
    }
}
