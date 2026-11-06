package com.uiptv.util;

import com.uiptv.model.Account;

import java.util.Set;
import java.util.function.Function;

/**
 * Allocates stable, human-readable names for accounts created by a bulk import.
 *
 * <p>The unsuffixed name is always preferred. If it is already used, the first
 * generated suffix is {@code (2)}; {@code (1)} is deliberately never generated.
 * The reserved-name set lets a whole import batch share one namespace before any
 * of its accounts have been persisted.</p>
 */
public final class AccountNameAllocator {
    private final Function<String, Account> accountProvider;

    public AccountNameAllocator(Function<String, Account> accountProvider) {
        this.accountProvider = accountProvider;
    }

    public String next(String url, Set<String> reservedNames) {
        if (!UiptUtils.isValidURL(url)) {
            return url;
        }
        String baseName = UiptUtils.getNameFromUrl(url);
        if (isAvailable(baseName, reservedNames)) {
            return baseName;
        }

        for (int suffix = 2; ; suffix++) {
            String candidate = baseName + "(" + suffix + ")";
            if (isAvailable(candidate, reservedNames)) {
                return candidate;
            }
        }
    }

    private boolean isAvailable(String name, Set<String> reservedNames) {
        return name != null
                && !reservedNames.contains(name)
                && accountProvider.apply(name) == null;
    }
}
