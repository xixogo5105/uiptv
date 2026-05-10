package com.uiptv.service;

import com.uiptv.model.Configuration;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("java:S6548")
public class FilterLockService {
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String HASH_PREFIX = "pbkdf2-sha256";
    private static final int SALT_BYTES = 32;
    private static final int HASH_BYTES = 32;
    private static final int ITERATIONS = 600_000;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final long UNLOCK_WINDOW_MS = 10L * 60L * 1000L;

    private final SecureRandom secureRandom = new SecureRandom();
    private final AtomicLong unlockedUntilEpochMs = new AtomicLong(0);
    private final AtomicReference<String> unlockedHashSnapshot = new AtomicReference<>("");

    private FilterLockService() {
    }

    private static class SingletonHelper {
        private static final FilterLockService INSTANCE = new FilterLockService();
    }

    public static FilterLockService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public boolean hasPasswordConfigured() {
        return !blank(currentHash());
    }

    public boolean isUnlocked() {
        String currentHash = currentHash();
        if (blank(currentHash)) {
            return true;
        }
        if (!currentHash.equals(unlockedHashSnapshot.get())) {
            clearUnlockSession();
            return false;
        }
        long expiresAt = unlockedUntilEpochMs.get();
        if (System.currentTimeMillis() >= expiresAt) {
            clearUnlockSession();
            return false;
        }
        return true;
    }

    public void clearUnlockSession() {
        unlockedUntilEpochMs.set(0);
        unlockedHashSnapshot.set("");
    }

    public boolean unlockWithPassword(String password) {
        String currentHash = currentHash();
        if (blank(currentHash)) {
            return true;
        }
        if (!verifyPassword(password, currentHash)) {
            return false;
        }
        unlockedHashSnapshot.set(currentHash);
        unlockedUntilEpochMs.set(System.currentTimeMillis() + UNLOCK_WINDOW_MS);
        return true;
    }

    public void applyInitialPassword(Configuration configuration, String newPassword) {
        requireStrongPassword(newPassword);
        configuration.setFilterLockHash(hashPassword(newPassword));
        clearUnlockSession();
    }

    public void applyPasswordChange(Configuration configuration, String currentPassword, String newPassword) {
        String existingHash = currentHash();
        if (blank(existingHash)) {
            throw new IllegalArgumentException("filterLockPasswordNotSet");
        }
        if (!verifyPassword(currentPassword, existingHash)) {
            throw new IllegalArgumentException("filterLockCurrentPasswordInvalid");
        }
        requireStrongPassword(newPassword);
        configuration.setFilterLockHash(hashPassword(newPassword));
        clearUnlockSession();
    }

    public void clearPassword(Configuration configuration, String currentPassword) {
        String existingHash = currentHash();
        if (blank(existingHash)) {
            throw new IllegalArgumentException("filterLockPasswordNotSet");
        }
        if (!verifyPassword(currentPassword, existingHash)) {
            throw new IllegalArgumentException("filterLockCurrentPasswordInvalid");
        }
        configuration.setFilterLockHash("");
        clearUnlockSession();
    }

    public int getMinPasswordLength() {
        return MIN_PASSWORD_LENGTH;
    }

    public long getUnlockWindowMinutes() {
        return UNLOCK_WINDOW_MS / 60_000L;
    }

    private void requireStrongPassword(String password) {
        if (password == null || password.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("filterLockPasswordTooShort");
        }
    }

    private String currentHash() {
        Configuration configuration = ConfigurationService.getInstance().read();
        return configuration == null ? "" : safe(configuration.getFilterLockHash());
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS, HASH_BYTES);
        return HASH_PREFIX
                + "$" + ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derived);
    }

    private boolean verifyPassword(String password, String storedHash) {
        if (password == null || blank(storedHash)) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception _) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt, int iterations, int hashBytes) {
        char[] chars = password.toCharArray();
        try {
            PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, hashBytes * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(HASH_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive filter lock hash", e);
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
