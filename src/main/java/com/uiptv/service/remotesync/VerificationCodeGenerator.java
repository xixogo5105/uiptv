package com.uiptv.service.remotesync;

import java.security.SecureRandom;

public final class VerificationCodeGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodeGenerator() {
    }

    public static String createFourDigitCode() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }
}
