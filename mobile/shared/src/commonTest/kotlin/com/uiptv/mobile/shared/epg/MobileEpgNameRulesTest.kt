package com.uiptv.mobile.shared.epg

import kotlin.test.*

class MobileEpgNameRulesTest {
    @Test fun languageCountryAndPipeLabelsKeepTimeshiftsAndConflictsDistinct() {
        val labels=listOf("hindi", "hi", "en", "english", "uk", "us", "arabic", "ar", "french", "fr", "tamil", "ta", "hd", "fhd", "|")
        val rules=MobileEpgNameRules(labels.map { MobileEpgNameRules.Rule(it,"quality") })
        for (label in labels.dropLast(3)) assertEquals("stargold", rules.quality("$label| STAR GOLD FHD"))
        assertEquals("history", rules.quality("History HD"))
        assertNotEquals(rules.quality("Star Gold"),rules.quality("Star Gold +1"))
        assertFalse(rules.aliases(mapOf("UK|Station" to "uk", "US|Station" to "us"),true).containsKey("station"))
    }

    @Test fun artworkIgnoresAliasesWithoutArtworkWhenCheckingAmbiguity() {
        val rules=MobileEpgNameRules(listOf(MobileEpgNameRules.Rule("hd","quality")))
        val guide=MobileXmltvGuide(emptyMap(),mapOf("bbc two" to "empty-sd","bbc two hd" to "hd"),
            icons=mapOf("hd" to "https://example.invalid/station.png")).withNameRules(rules)
        assertEquals("https://example.invalid/station.png",guide.iconForChannel("playlist-id","BBC Two Ⓖ"))
        assertEquals("",guide.iconForChannel("playlist-id","BBC Two +1"))
    }

    @Test fun databaseRulesApplySymmetricallyAndCanBeReplacedWithoutRetainingOldMatches() {
        val p=MobileEpgProgramme("Programme","",1000,2000)
        val guide=MobileXmltvGuide(mapOf("station" to listOf(p)),mapOf("station" to "station"))
        val disabled=MobileEpgNameRules(emptyList())
        val enabled=MobileEpgNameRules(listOf(MobileEpgNameRules.Rule("trial","annotation")))
        assertTrue(guide.withNameRules(disabled).forChannel("playlist-id","Station [Trial]").isEmpty())
        assertEquals(listOf(p),guide.withNameRules(enabled).forChannel("playlist-id","Station [Trial]"))
        assertTrue(guide.withNameRules(disabled).forChannel("playlist-id","Station [Trial]").isEmpty())
        assertEquals(listOf(p),guide.withNameRules(disabled).forChannel("station","Unrelated"))
        assertEquals(listOf(p),guide.copy(names=mapOf("station [trial]" to "station"))
            .withNameRules(enabled).forChannel("playlist-id","Station"))
    }
    @Test fun rulesAreLiteralAndPreserveRegionsNumbersTimeshiftsAndConflicts() {
        val rules=MobileEpgNameRules(listOf(MobileEpgNameRules.Rule("504p","annotation"),
            MobileEpgNameRules.Rule("not 24/7","annotation"),MobileEpgNameRules.Rule("hd","quality"),
            MobileEpgNameRules.Rule("sd","quality"),MobileEpgNameRules.Rule("a.*","annotation")))
        assertEquals("station",rules.display("Station (504p) [Not 24/7]"))
        assertEquals("station hd",rules.display("Station HD"))
        assertEquals("station",rules.quality("Station HD"))
        assertEquals(rules.quality("Nick HD+"),rules.quality("Nick HD +"))
        assertEquals("",rules.quality("##### MOVIES HEVC #####"))
        assertEquals("station",rules.display("Station [a.*]"))
        assertEquals("station [abc]",rules.display("Station [abc]"))
        assertNotEquals(rules.quality("Station"),rules.quality("Station +1"))
        assertNotEquals(rules.quality("Station"),rules.quality("Station West"))
        assertEquals("24/7 movies",rules.display("24/7 Movies"))
        assertFalse(rules.aliases(mapOf("Station HD" to "hd","Station SD" to "sd"),true).containsKey("station"))
        val guide=MobileXmltvGuide(mapOf("hd" to listOf(MobileEpgProgramme("HD","",1,2)),
            "sd" to listOf(MobileEpgProgramme("SD","",1,2))),mapOf("station hd" to "hd","station sd" to "sd"))
            .withNameRules(rules)
        assertEquals("HD",guide.forChannel("playlist-id","Station HD (504p)").single().title)
        assertTrue(guide.forChannel("playlist-id","Station").isEmpty())
    }
}
