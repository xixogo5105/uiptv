package com.uiptv.server;

import com.uiptv.util.Platform;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class LocalHttpsCertificateStore {
    private static final String KEYSTORE_BASENAME = "uiptv-local-https";
    private static final String KEYSTORE_FILENAME = KEYSTORE_BASENAME + ".p12";
    private static final String KEYSTORE_PROTECTION_FILENAME = KEYSTORE_BASENAME + ".bin";
    private static final String KEY_ALIAS = KEYSTORE_BASENAME;
    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private LocalHttpsCertificateStore() {
    }

    static SSLContext sslContext(Collection<String> bindHosts) throws IOException {
        try {
            char[] protection = loadOrCreateKeyStoreProtection();
            KeyStore keyStore = loadOrCreateKeyStore(bindHosts, protection);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, protection);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, SECURE_RANDOM);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to initialize HTTPS certificate", e);
        }
    }

    private static KeyStore loadOrCreateKeyStore(Collection<String> bindHosts, char[] protection)
            throws IOException, GeneralSecurityException {
        Path keyStorePath = keyStorePath();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        if (Files.exists(keyStorePath)) {
            try (var input = Files.newInputStream(keyStorePath)) {
                keyStore.load(input, protection);
                return keyStore;
            } catch (IOException | GeneralSecurityException _) {
                Files.deleteIfExists(keyStorePath);
            }
        }

        Files.createDirectories(keyStorePath.getParent());
        keyStore.load(null, protection);
        KeyPair keyPair = generateKeyPair();
        X509Certificate certificate = generateCertificate(keyPair, certificateHosts(bindHosts));
        keyStore.setKeyEntry(KEY_ALIAS, keyPair.getPrivate(), protection, new Certificate[]{certificate});
        try (OutputStream output = Files.newOutputStream(keyStorePath)) {
            keyStore.store(output, protection);
        }
        return keyStore;
    }

    private static char[] loadOrCreateKeyStoreProtection() throws IOException {
        Path path = keyStoreProtectionPath();
        if (Files.exists(path)) {
            String value = Files.readString(path, StandardCharsets.US_ASCII).trim();
            if (!value.isBlank()) {
                return value.toCharArray();
            }
        }

        Files.createDirectories(path.getParent());
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Files.writeString(path, value, StandardCharsets.US_ASCII);
        return value.toCharArray();
    }

    private static Path keyStorePath() {
        return Path.of(Platform.getUserHomeDirPath(), KEYSTORE_FILENAME);
    }

    private static Path keyStoreProtectionPath() {
        return Path.of(Platform.getUserHomeDirPath(), KEYSTORE_PROTECTION_FILENAME);
    }

    private static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, SECURE_RANDOM);
        return generator.generateKeyPair();
    }

    private static Set<String> certificateHosts(Collection<String> bindHosts) {
        Set<String> hosts = new LinkedHashSet<>();
        hosts.add("localhost");
        hosts.add("127.0.0.1");
        hosts.add("::1");
        if (bindHosts != null) {
            bindHosts.stream()
                    .filter(host -> host != null && !host.isBlank())
                    .map(String::trim)
                    .forEach(hosts::add);
        }
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.isBlank()) {
                hosts.add(hostName.trim());
            }
        } catch (Exception _) {
            // Hostname SANs are best-effort; loopback and bound IPs are enough for local HTTPS.
        }
        return hosts;
    }

    private static X509Certificate generateCertificate(KeyPair keyPair, Set<String> hosts)
            throws GeneralSecurityException, IOException {
        Instant now = Instant.now();
        byte[] algorithmIdentifier = algorithmIdentifier("1.2.840.113549.1.1.11");
        byte[] subject = name("UIPTV Local HTTPS");
        byte[] tbsCertificate = sequence(
                explicit(0, integer(BigInteger.valueOf(2))),
                integer(randomSerial()),
                algorithmIdentifier,
                subject,
                sequence(generalizedTime(now.minus(1, ChronoUnit.DAYS)), generalizedTime(now.plus(3650, ChronoUnit.DAYS))),
                subject,
                keyPair.getPublic().getEncoded(),
                explicit(3, extensions(hosts))
        );

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate(), SECURE_RANDOM);
        signature.update(tbsCertificate);
        byte[] certificate = sequence(
                tbsCertificate,
                algorithmIdentifier,
                bitString(signature.sign())
        );
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificate));
    }

    private static BigInteger randomSerial() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        bytes[0] = (byte) (bytes[0] & 0x7F);
        return new BigInteger(1, bytes);
    }

    private static byte[] extensions(Set<String> hosts) throws IOException {
        return sequence(
                extension("2.5.29.17", false, generalNames(hosts)),
                extension("2.5.29.19", false, sequence()),
                extension("2.5.29.37", false, sequence(objectIdentifier("1.3.6.1.5.5.7.3.1")))
        );
    }

    private static byte[] extension(String oid, boolean critical, byte[] value) throws IOException {
        if (critical) {
            return sequence(objectIdentifier(oid), bool(true), octetString(value));
        }
        return sequence(objectIdentifier(oid), octetString(value));
    }

    private static byte[] generalNames(Set<String> hosts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String host : hosts) {
            if (host == null || host.isBlank()) {
                continue;
            }
            String normalized = host.trim();
            if (isIpLiteral(normalized)) {
                try {
                    out.write(tag(0x87, InetAddress.getByName(normalized).getAddress()));
                } catch (Exception _) {
                    // Skip malformed literals rather than failing certificate creation.
                }
            } else {
                out.write(tag(0x82, normalized.getBytes(StandardCharsets.US_ASCII)));
            }
        }
        return tag(0x30, out.toByteArray());
    }

    private static boolean isIpLiteral(String host) {
        return host.contains(":") || IPV4_LITERAL.matcher(host).matches();
    }

    private static byte[] name(String commonName) throws IOException {
        return sequence(set(sequence(objectIdentifier("2.5.4.3"), utf8String(commonName))));
    }

    private static byte[] algorithmIdentifier(String oid) throws IOException {
        return sequence(objectIdentifier(oid), tag(0x05, new byte[0]));
    }

    private static byte[] integer(BigInteger value) throws IOException {
        return tag(0x02, value.toByteArray());
    }

    private static byte[] bool(boolean value) throws IOException {
        return tag(0x01, new byte[]{(byte) (value ? 0xFF : 0x00)});
    }

    private static byte[] generalizedTime(Instant instant) throws IOException {
        String text = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")
                .withZone(java.time.ZoneOffset.UTC)
                .format(instant);
        return tag(0x18, text.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] objectIdentifier(String oid) throws IOException {
        String[] rawParts = oid.split("\\.");
        if (rawParts.length < 2) {
            throw new IOException("Invalid object identifier: " + oid);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int first = Integer.parseInt(rawParts[0]);
        int second = Integer.parseInt(rawParts[1]);
        writeOidPart(out, first * 40L + second);
        for (int i = 2; i < rawParts.length; i++) {
            writeOidPart(out, Long.parseLong(rawParts[i]));
        }
        return tag(0x06, out.toByteArray());
    }

    private static void writeOidPart(ByteArrayOutputStream out, long value) {
        byte[] stack = new byte[10];
        int index = stack.length;
        stack[--index] = (byte) (value & 0x7F);
        value >>= 7;
        while (value > 0) {
            stack[--index] = (byte) ((value & 0x7F) | 0x80);
            value >>= 7;
        }
        out.write(stack, index, stack.length - index);
    }

    private static byte[] utf8String(String value) throws IOException {
        return tag(0x0C, value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] octetString(byte[] value) throws IOException {
        return tag(0x04, value);
    }

    private static byte[] bitString(byte[] value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(value);
        return tag(0x03, out.toByteArray());
    }

    private static byte[] sequence(byte[]... values) throws IOException {
        return constructed(0x30, values);
    }

    private static byte[] set(byte[]... values) throws IOException {
        return constructed(0x31, values);
    }

    private static byte[] explicit(int tagNumber, byte[] value) throws IOException {
        return tag(0xA0 + tagNumber, value);
    }

    private static byte[] constructed(int tag, byte[]... values) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) {
            out.write(value);
        }
        return tag(tag, out.toByteArray());
    }

    private static byte[] tag(int tag, byte[] value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        writeLength(out, value.length);
        out.write(value);
        return out.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 128) {
            out.write(length);
            return;
        }
        int size = Integer.BYTES - Integer.numberOfLeadingZeros(length) / 8;
        out.write(0x80 | size);
        for (int i = size - 1; i >= 0; i--) {
            out.write((length >> (8 * i)) & 0xFF);
        }
    }
}
