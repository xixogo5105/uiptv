package com.uiptv.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacAddressVerificationUtilsTest {
    @Test
    void removesInvalidAddressesRegardlessOfCaseOrSpacing() {
        List<String> addresses = List.of(
                "00:1A:79:45:56:54",
                "00:1A:79:5D:01:29",
                "00:1A:79:05:C6:A0"
        );

        assertEquals(
                List.of("00:1A:79:45:56:54", "00:1A:79:05:C6:A0"),
                MacAddressVerificationUtils.removeInvalidAddresses(
                        addresses,
                        List.of("00:1a:79:5d:01:29")
                )
        );
    }

    @Test
    void matchesInvalidDefaultRegardlessOfCaseOrSpacing() {
        assertTrue(MacAddressVerificationUtils.containsInvalidAddress(
                List.of("00:1A:79:5D:01:29"),
                " 00:1a:79:5d:01:29 "
        ));
    }

    @Test
    void checksDefaultMembershipByAddressInsteadOfSubstring() {
        List<String> addresses = List.of("00:1A:79:45:56:54", "00:1A:79:05:C6:A0");

        assertTrue(MacAddressVerificationUtils.containsAddress(addresses, "00:1a:79:45:56:54"));
        assertFalse(MacAddressVerificationUtils.containsAddress(addresses, "00:1A:79:45"));
    }
}
