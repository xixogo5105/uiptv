package com.uiptv.service

import com.uiptv.model.Configuration
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object FilterLockService {
    private const val HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val HASH_PREFIX = "pbkdf2-sha256"
    private const val SALT_BYTES = 32
    private const val HASH_BYTES = 32
    private const val ITERATIONS = 600_000
    private const val MIN_PASSWORD_LENGTH = 6
    private const val UNLOCK_WINDOW_MS = 10L * 60L * 1000L

    private val secureRandom = SecureRandom()
    private val unlockedUntilEpochMs = AtomicLong(0)
    private val unlockedHashSnapshot = AtomicReference("")

    @JvmStatic
    fun getInstance(): FilterLockService = this
    fun hasPasswordConfigured(): Boolean = !blank(currentHash())
    fun isUnlocked(): Boolean {
        val currentHash = currentHash()
        if (blank(currentHash)) {
            return true
        }
        if (currentHash != unlockedHashSnapshot.get()) {
            clearUnlockSession()
            return false
        }
        val expiresAt = unlockedUntilEpochMs.get()
        if (System.currentTimeMillis() >= expiresAt) {
            clearUnlockSession()
            return false
        }
        return true
    }
    fun clearUnlockSession() {
        unlockedUntilEpochMs.set(0)
        unlockedHashSnapshot.set("")
    }
    fun unlockWithPassword(password: String?): Boolean {
        val currentHash = currentHash()
        if (blank(currentHash)) {
            return true
        }
        if (!verifyPassword(password, currentHash)) {
            return false
        }
        unlockedHashSnapshot.set(currentHash)
        unlockedUntilEpochMs.set(System.currentTimeMillis() + UNLOCK_WINDOW_MS)
        return true
    }
    fun applyInitialPassword(configuration: Configuration, newPassword: String?) {
        requireStrongPassword(newPassword)
        configuration.filterLockHash = hashPassword(newPassword.orEmpty())
        clearUnlockSession()
    }
    fun applyPasswordChange(configuration: Configuration, currentPassword: String?, newPassword: String?) {
        val existingHash = currentHash()
        if (blank(existingHash)) {
            throw IllegalArgumentException("filterLockPasswordNotSet")
        }
        if (!verifyPassword(currentPassword, existingHash)) {
            throw IllegalArgumentException("filterLockCurrentPasswordInvalid")
        }
        requireStrongPassword(newPassword)
        configuration.filterLockHash = hashPassword(newPassword.orEmpty())
        clearUnlockSession()
    }
    fun clearPassword(configuration: Configuration, currentPassword: String?) {
        val existingHash = currentHash()
        if (blank(existingHash)) {
            throw IllegalArgumentException("filterLockPasswordNotSet")
        }
        if (!verifyPassword(currentPassword, existingHash)) {
            throw IllegalArgumentException("filterLockCurrentPasswordInvalid")
        }
        configuration.filterLockHash = ""
        clearUnlockSession()
    }
    fun getMinPasswordLength(): Int = MIN_PASSWORD_LENGTH
    fun getUnlockWindowMinutes(): Long = UNLOCK_WINDOW_MS / 60_000L

    private fun requireStrongPassword(password: String?) {
        if (password == null || password.trim().length < MIN_PASSWORD_LENGTH) {
            throw IllegalArgumentException("filterLockPasswordTooShort")
        }
    }

    private fun currentHash(): String = safe(ConfigurationService.getInstance().read().filterLockHash)

    private fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = derive(password, salt, ITERATIONS, HASH_BYTES)
        return HASH_PREFIX +
            "$" + ITERATIONS +
            "$" + Base64.getEncoder().encodeToString(salt) +
            "$" + Base64.getEncoder().encodeToString(derived)
    }

    private fun verifyPassword(password: String?, storedHash: String?): Boolean {
        if (password == null || blank(storedHash)) {
            return false
        }
        val parts = storedHash.orEmpty().split("$")
        if (parts.size != 4 || HASH_PREFIX != parts[0]) {
            return false
        }
        return try {
            val iterations = parts[1].toInt()
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            val actual = derive(password, salt, iterations, expected.size)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int, hashBytes: Int): ByteArray {
        val chars = password.toCharArray()
        try {
            val spec = PBEKeySpec(chars, salt, iterations, hashBytes * 8)
            val factory = SecretKeyFactory.getInstance(HASH_ALGORITHM)
            return factory.generateSecret(spec).encoded
        } catch (e: Exception) {
            throw IllegalStateException("Unable to derive filter lock hash", e)
        } finally {
            java.util.Arrays.fill(chars, '\u0000')
        }
    }

    private fun blank(value: String?): Boolean = value == null || value.trim().isEmpty()

    private fun safe(value: String?): String {
        if (value == null) {
            return ""
        }
        return String(value.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
    }
}
