package com.uiptv.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EpgTerritoryTest {
    static Stream<org.junit.jupiter.params.provider.Arguments> requestedAliases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("UK", "GB"),
                org.junit.jupiter.params.provider.Arguments.of("GBR", "GB"),
                org.junit.jupiter.params.provider.Arguments.of("United Kingdom", "GB"),
                org.junit.jupiter.params.provider.Arguments.of("IN", "IN"),
                org.junit.jupiter.params.provider.Arguments.of("IND", "IN"),
                org.junit.jupiter.params.provider.Arguments.of("India", "IN"),
                org.junit.jupiter.params.provider.Arguments.of("Bharat", "IN"),
                org.junit.jupiter.params.provider.Arguments.of("Hindustan", "IN"),
                org.junit.jupiter.params.provider.Arguments.of("PK", "PK"),
                org.junit.jupiter.params.provider.Arguments.of("PAK", "PK"),
                org.junit.jupiter.params.provider.Arguments.of("Pakistan", "PK"),
                org.junit.jupiter.params.provider.Arguments.of("Islamic Republic of Pakistan", "PK"),
                org.junit.jupiter.params.provider.Arguments.of("USA", "US"),
                org.junit.jupiter.params.provider.Arguments.of("United States of America", "US"));
    }

    @ParameterizedTest
    @MethodSource("requestedAliases")
    void recognizesCommonCountryAliases(String alias, String expected) {
        assertEquals(expected, EpgTerritory.parse("Example Sports " + alias).territory());
    }

    @Test
    void recognizesEveryJdkCountryCodeAndDisplayNameWithoutChannelSpecificData() {
        for (String alpha2 : Locale.getISOCountries()) {
            Locale country = new Locale("", alpha2);
            if (!alpha2.equals("SD"))
                assertEquals(alpha2, EpgTerritory.parse("Example Sports " + alpha2).territory(), alpha2);
            String alpha3 = country.getISO3Country();
            if (!alpha3.isBlank()) assertEquals(alpha2, EpgTerritory.parse("Example Sports " + alpha3).territory(), alpha3);
            var display = EpgTerritory.parse("Example Sports " + country.getDisplayCountry(Locale.ENGLISH));
            if (display.ambiguous()) assertTrue(display.territories().contains(alpha2), alpha2);
            else assertEquals(alpha2, display.territory(), alpha2);
        }
    }

    @Test
    void resolvesQualityCodeCollisionOnlyWhenCountryMarkerIsExplicit() {
        assertTrue(EpgTerritory.parse("Example Sports SD").territories().isEmpty());
        assertEquals(Set.of("SD"), EpgTerritory.parse("Example Sports (SD)").territories());
        assertEquals(Set.of("SD"), EpgTerritory.parseCountries("SD"));
    }

    @Test
    void languageGroupLabelsDoNotBecomeDistributionTerritories() {
        for (String language : List.of("EN", "HINDI", "AR", "DE", "FR", "ES"))
            assertTrue(EpgTerritory.parse(language + " | Example Sports").territories().isEmpty(), language);
        assertEquals("IN", EpgTerritory.parse("IN | Example Sports").territory());
        assertEquals("example sports", EpgTerritory.parse("IN | Example Sports").name());
        assertEquals("GB", EpgTerritory.parse("UK | Example Sports").territory());
    }

    @Test
    void understandsBracketedAndTrailingMarkersWithoutRemovingMeaningfulVariant() {
        assertEquals("GB", EpgTerritory.parse("Example Sports (United Kingdom-HD)").territory());
        assertEquals("US", EpgTerritory.parse("Example Sports - USA").territory());
        assertEquals("IN", EpgTerritory.parse("Example Sports Bharat 4K").territory());
        assertEquals("example sports +1", EpgTerritory.parse("Example Sports +1 (UK-HD)").name());
    }

    @Test
    void sourceIdentityUsesCountrySuffixesAsFallbackMetadata() {
        assertEquals(Set.of("IN"), EpgTerritory.identifierCountries("example.sports.in"));
        assertEquals(Set.of("GB"), EpgTerritory.identifierCountries("example.sports.GBR"));
        assertEquals(Set.of("US"), EpgTerritory.sourceCountries("", "source", "example.sports.usa"));
    }
}
