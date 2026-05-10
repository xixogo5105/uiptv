package com.uiptv.util

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

object AppLog {
    private val listeners = CopyOnWriteArrayList<Consumer<String>>()
    private const val MAX_LOG_LENGTH = 4000
    private const val SHOW_LOGS_PROPERTY = "uiptv.showLogs"

    @JvmStatic
    fun addInfoLog(logSource: Class<*>, log: String?) {
        logWithLevel(logSource, log, LogLevel.INFO)
    }

    @JvmStatic
    fun addWarningLog(logSource: Class<*>, log: String?) {
        logWithLevel(logSource, log, LogLevel.WARNING)
    }

    @JvmStatic
    fun addErrorLog(logSource: Class<*>, log: String?) {
        logWithLevel(logSource, log, LogLevel.ERROR)
    }

    @JvmStatic
    fun addLog(logSource: Class<*>, log: String?) {
        addInfoLog(logSource, log)
    }

    private fun logWithLevel(logSource: Class<*>, log: String?, level: LogLevel) {
        requireNotNull(logSource) { "logSource cannot be null" }
        val safeLog = sanitizeLogMessage(log)
        if (isTerminalLoggingEnabled()) {
            val logger = LoggerFactory.getLogger(logSource)
            when (level) {
                LogLevel.ERROR -> logger.error(safeLog)
                LogLevel.WARNING -> logger.warn(safeLog)
                LogLevel.INFO -> logger.info(safeLog)
            }
        }
        for (listener in listeners) {
            try {
                listener.accept(safeLog)
            } catch (e: Exception) {
                LoggerFactory.getLogger(AppLog::class.java).error("Log listener failed", e)
            }
        }
    }

    @JvmStatic
    fun sanitizeValue(value: String?): String = sanitizeLogMessage(value)

    private fun sanitizeLogMessage(message: String?): String {
        if (message == null) {
            return ""
        }
        val normalized = buildString(message.length) {
            message.forEach { current -> append(if (Character.isISOControl(current)) ' ' else current) }
        }.trim()
        return if (normalized.length <= MAX_LOG_LENGTH) normalized else normalized.substring(0, MAX_LOG_LENGTH) + "..."
    }

    @JvmStatic
    fun registerListener(listener: Consumer<String>?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    @JvmStatic
    fun unregisterListener(listener: Consumer<String>?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    private fun isTerminalLoggingEnabled(): Boolean =
        java.lang.Boolean.parseBoolean(System.getProperty(SHOW_LOGS_PROPERTY, "false"))

    private enum class LogLevel {
        INFO,
        WARNING,
        ERROR
    }
}
