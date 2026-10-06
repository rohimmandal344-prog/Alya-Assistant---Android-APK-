package com.example.core.logger

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

data class LogEntry(
    val id: Long,
    val tag: String,
    val level: String,
    val message: String,
    val timestamp: String
)

object AlyaLogger {
    const val TAG_AUDIO = "ALYA_AUDIO"
    const val TAG_LIVE = "ALYA_LIVE"
    const val TAG_STT = "ALYA_STT"
    const val TAG_TTS = "ALYA_TTS"
    const val TAG_AI = "ALYA_AI"
    const val TAG_DEVICE = "ALYA_DEVICE"
    const val TAG_ACCESSIBILITY = "ALYA_ACCESSIBILITY"
    const val TAG_PERMISSION = "ALYA_PERMISSION"
    const val TAG_AUTH = "ALYA_AUTH"
    const val TAG_UI = "ALYA_UI"

    private const val MAX_LOGS = 120
    private val logQueue = ConcurrentLinkedDeque<LogEntry>()
    private var sequenceId = 0L

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        val sanitized = sanitize(message)
        Log.d(tag, sanitized)
        record(tag, "DEBUG", sanitized)
    }

    fun i(tag: String, message: String) {
        val sanitized = sanitize(message)
        Log.i(tag, sanitized)
        record(tag, "INFO", sanitized)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        Log.w(tag, sanitized, throwable)
        val text = if (throwable != null) "$sanitized (${throwable.javaClass.simpleName}: ${throwable.message})" else sanitized
        record(tag, "WARN", text)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = sanitize(message)
        Log.e(tag, sanitized, throwable)
        val text = if (throwable != null) "$sanitized (${throwable.javaClass.simpleName}: ${throwable.message})" else sanitized
        record(tag, "ERROR", text)
    }

    private fun record(tag: String, level: String, message: String) {
        val entry = LogEntry(
            id = ++sequenceId,
            tag = tag,
            level = level,
            message = message,
            timestamp = timeFormat.format(Date())
        )
        logQueue.addFirst(entry)
        while (logQueue.size > MAX_LOGS) {
            logQueue.removeLast()
        }
        _logsFlow.value = logQueue.toList()
    }

    fun clear() {
        logQueue.clear()
        _logsFlow.value = emptyList()
    }

    /**
     * Prevents accidental logging of API keys, tokens, or passwords
     */
    private fun sanitize(input: String): String {
        return input.replace(Regex("(?i)(key|secret|password|token|bearer)=['\"]?[a-zA-Z0-9_-]{8,}['\"]?"), "$1=***REDACTED***")
    }
}
