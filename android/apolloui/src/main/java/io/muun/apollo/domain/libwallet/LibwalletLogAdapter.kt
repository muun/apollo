package io.muun.apollo.domain.libwallet

import android.util.Log
import app_provided_data.AppLogSink
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.utils.isEmpty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

class LibwalletLogAdapter : AppLogSink {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun getDefaultLogLevel(): Long {
        // This is passed to go code. The magic numbers match the levels in the slog package.
        return if (Globals.INSTANCE.isDogfood || Globals.INSTANCE.isDebug) {
            -4 // slog.LevelDebug
        } else {
            0 // slog.LevelInfo
        }
    }

    override fun write(logBytes: ByteArray?): Long {
        if (logBytes == null) {
            return 0
        }

        val logLine = logBytes.toString(Charsets.UTF_8)
        val jsonLog = json.parseToJsonElement(logLine).jsonObject

        val level = extractLevel(jsonLog)
        val message = extractMessage(jsonLog)
        val sourceLocation = extractSourceLocation(jsonLog)

        Timber.tag(sourceLocation).log(level, message)

        return logBytes.size.toLong()
    }

    /**
     * Return a message extracted from the json log object.
     *
     * The message will include the msg field unaltered (or "[MISSING MESSAGE]" if missing)
     * followed by any extra values that can be found at the top level of the objects as
     * formatted by extractExtraValues().
     */
    private fun extractMessage(jsonLog: JsonObject): String {
        val message: String = jsonLog["msg"]?.jsonPrimitive?.contentOrNull ?: "[MISSING MESSAGE]"
        val extraValues = extractExtraValues(jsonLog)
        return if (extraValues.isEmpty())
            message
        else
            "$message $extraValues"
    }

    /**
     * Return a string encoding the source file and function that originates the log.
     */
    private fun extractSourceLocation(jsonLog: JsonObject): String {
        return jsonLog["source"]?.let { source ->
            json.decodeFromJsonElement<LibwalletLogSourceLocation>(source).let {
                val fileName = it.file
                val functionName = it.function
                "$fileName:$functionName"
            }
        } ?: "<libwallet>"
    }

    /**
     * Return all of the non-default top level attributes from the json log as a string.
     *
     * This skips over the time, msg, level and source attributes.
     */
    private fun extractExtraValues(jsonLog: JsonObject): String {
        val knownKeys = setOf("time", "msg", "level", "source")
        return jsonLog.entries
            .filter { !knownKeys.contains(it.key) }
            .joinToString { "${it.key}=${it.value}" }
    }

    /**
     * Return an android log level mapped from the json representation of the log.
     */
    private fun extractLevel(jsonLog: JsonObject): Int {
        val level = jsonLog["level"]?.jsonPrimitive?.contentOrNull
        return when (level) {
            "DEBUG" -> Log.DEBUG
            "INFO" -> Log.INFO
            "WARN" -> Log.WARN
            // If we're not sure, it's an error.
            else -> Log.ERROR
        }
    }
}

@Serializable
private data class LibwalletLogSourceLocation(
    val file: String = "<libwallet>",
    val line: Long = 0,
    val function: String = "<unknown>",
)
