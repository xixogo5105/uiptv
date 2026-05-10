package com.uiptv.service.remotesync

import java.security.SecureRandom

object VerificationCodeGenerator {
    private val random = SecureRandom()

    @JvmStatic
    fun createFourDigitCode(): String = "%04d".format(random.nextInt(10_000))
}
