package com.uiptv.service.remotesync;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

final class RemoteSyncTransferCipher {
    private static final byte[] MAGIC = "UIPTVRS1".getBytes(StandardCharsets.US_ASCII);
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RemoteSyncTransferCipher() {
    }

    static void encrypt(Path source, Path destination, String verificationCode, String sessionId) throws IOException {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] nonce = randomBytes(NONCE_BYTES);
        Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, verificationCode, sessionId, salt, nonce);
        try (OutputStream fileOutput = Files.newOutputStream(destination);
             BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutput)) {
            bufferedOutput.write(MAGIC);
            bufferedOutput.write(salt);
            bufferedOutput.write(nonce);
            try (CipherOutputStream cipherOutput = new CipherOutputStream(bufferedOutput, cipher);
                 InputStream input = Files.newInputStream(source)) {
                input.transferTo(cipherOutput);
            }
        }
    }

    static void decrypt(Path source, Path destination, String verificationCode, String sessionId) throws IOException {
        try (InputStream fileInput = Files.newInputStream(source);
             BufferedInputStream bufferedInput = new BufferedInputStream(fileInput)) {
            byte[] magic = bufferedInput.readNBytes(MAGIC.length);
            if (!Arrays.equals(MAGIC, magic)) {
                throw new IOException("Remote sync transfer is not encrypted.");
            }
            byte[] salt = readExact(bufferedInput, SALT_BYTES, "salt");
            byte[] nonce = readExact(bufferedInput, NONCE_BYTES, "nonce");
            Cipher cipher = initCipher(Cipher.DECRYPT_MODE, verificationCode, sessionId, salt, nonce);
            try (CipherInputStream cipherInput = new CipherInputStream(bufferedInput, cipher);
                 OutputStream output = Files.newOutputStream(destination)) {
                cipherInput.transferTo(output);
            }
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] readExact(InputStream input, int size, String label) throws IOException {
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size) {
            throw new IOException("Remote sync encrypted transfer is missing " + label + ".");
        }
        return bytes;
    }

    private static Cipher initCipher(int mode,
                                     String verificationCode,
                                     String sessionId,
                                     byte[] salt,
                                     byte[] nonce) throws IOException {
        try {
            SecretKeySpec key = new SecretKeySpec(deriveKey(verificationCode, sessionId, salt), "AES");
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher;
        } catch (GeneralSecurityException ex) {
            throw new IOException("Unable to prepare remote sync transfer cipher.", ex);
        }
    }

    private static byte[] deriveKey(String verificationCode, String sessionId, byte[] salt) throws GeneralSecurityException {
        char[] password = passwordChars(verificationCode, sessionId);
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance(KEY_ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static char[] passwordChars(String verificationCode, String sessionId) {
        return ((verificationCode == null ? "" : verificationCode)
                + ":"
                + (sessionId == null ? "" : sessionId)).toCharArray();
    }
}
