package com.uiptv.ui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Helpers for applying the case-insensitive results of MAC address verification. */
final class MacAddressVerificationUtils {
    private MacAddressVerificationUtils() {
    }

    static List<String> removeInvalidAddresses(List<String> addresses, Collection<String> invalidAddresses) {
        Set<String> invalidKeys = invalidAddresses.stream()
                .map(MacAddressVerificationUtils::normalize)
                .collect(Collectors.toSet());
        return addresses.stream()
                .filter(address -> !invalidKeys.contains(normalize(address)))
                .toList();
    }

    static boolean containsInvalidAddress(Collection<String> invalidAddresses, String address) {
        return containsAddress(invalidAddresses, address);
    }

    static boolean containsAddress(Collection<String> addresses, String address) {
        String normalizedAddress = normalize(address);
        return !normalizedAddress.isEmpty() && addresses.stream()
                .map(MacAddressVerificationUtils::normalize)
                .anyMatch(normalizedAddress::equals);
    }

    private static String normalize(String address) {
        return address == null ? "" : address.replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }
}
