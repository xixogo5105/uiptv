package com.uiptv.util

import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.regex.Pattern

class CmdLineRunner {
    class CmdLineException(message: String, cause: Throwable) : Exception(message, cause)

    fun exec(cmd: String) {
        exec(parse(cmd), Collections.emptyMap())
    }

    fun exec(cmd: String, env: Map<String, String>) {
        exec(parse(cmd), env)
    }

    fun exec(cmd: List<String>, env: Map<String, String>) {
        try {
            val processBuilder = ProcessBuilder(cmd)
            processBuilder.environment().putAll(env)
            log.debug("Executing command: {} with ENV={}", cmd, processBuilder.environment())
            processBuilder.start()
        } catch (e: Exception) {
            throw CmdLineException("error executing command \"$cmd\"", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CmdLineRunner::class.java)
        private val CMD_PATTERN = Pattern.compile("\"([^\"]*)\"|\\S+")

        @JvmStatic
        fun parse(cmd: String): List<String> {
            require(!StringUtils.isBlank(cmd)) { "Command cannot be null or empty" }
            val result = ArrayList<String>()
            val matcher = CMD_PATTERN.matcher(cmd)
            while (matcher.find()) {
                if (matcher.group(1) != null) {
                    result.add(matcher.group(1))
                } else {
                    result.add(matcher.group())
                }
            }
            return result
        }
    }
}
