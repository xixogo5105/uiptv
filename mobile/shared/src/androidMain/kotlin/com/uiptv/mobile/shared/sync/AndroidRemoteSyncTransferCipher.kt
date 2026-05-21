package com.uiptv.mobile.shared.sync

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object AndroidRemoteSyncTransferCipher {
    private val magic = "UIPTVRS1".toByteArray(Charsets.US_ASCII)
    private const val keyAlgorithm = "PBKDF2WithHmacSHA1"
    private const val cipherAlgorithm = "AES/GCM/NoPadding"
    private const val iterations = 120_000
    private const val keyBits = 256
    private const val saltBytes = 16
    private const val nonceBytes = 12
    private const val tagBits = 128

    fun decrypt(source: File, destination: File, verificationCode: String, sessionId: String) {
        BufferedInputStream(source.inputStream()).use { input ->
            val header = input.readExact(magic.size, "header")
            require(Arrays.equals(magic, header)) { "Remote sync transfer is not encrypted." }
            val salt = input.readExact(saltBytes, "salt")
            val nonce = input.readExact(nonceBytes, "nonce")
            val cipher = initCipher(Cipher.DECRYPT_MODE, verificationCode, sessionId, salt, nonce)
            CipherInputStream(input, cipher).use { cipherInput ->
                BufferedOutputStream(destination.outputStream()).use { output ->
                    cipherInput.copyTo(output)
                }
            }
        }
    }

    private fun initCipher(
        mode: Int,
        verificationCode: String,
        sessionId: String,
        salt: ByteArray,
        nonce: ByteArray
    ): Cipher {
        val key = SecretKeySpec(deriveKey(verificationCode, sessionId, salt), "AES")
        return Cipher.getInstance(cipherAlgorithm).apply {
            init(mode, key, GCMParameterSpec(tagBits, nonce))
        }
    }

    private fun deriveKey(verificationCode: String, sessionId: String, salt: ByteArray): ByteArray {
        val password = "$verificationCode:$sessionId".toCharArray()
        return try {
            val spec = PBEKeySpec(password, salt, iterations, keyBits)
            SecretKeyFactory.getInstance(keyAlgorithm).generateSecret(spec).encoded
        } finally {
            password.fill('\u0000')
        }
    }

    private fun BufferedInputStream.readExact(size: Int, label: String): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(bytes, offset, size - offset)
            if (count < 0) {
                break
            }
            offset += count
        }
        require(offset == size) { "Remote sync encrypted transfer is missing $label." }
        return bytes
    }
}
