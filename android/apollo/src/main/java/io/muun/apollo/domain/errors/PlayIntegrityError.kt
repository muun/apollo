package io.muun.apollo.domain.errors

const val UNKNOWN_ERROR = "UNKNOWN_ERROR"

open class PlayIntegrityError : MuunError {

    constructor() : super("Google Play Integrity Error") {
        metadata["name"] = "CANCELLED"
        metadata["text"] = "Task was canceled"
    }

    constructor(cause: Throwable) : super("Google Play Integrity Error", cause) {
        metadata["name"] = UNKNOWN_ERROR
        metadata["text"] = cause.message ?: cause.toString()
        metadata["cause"] = cause.message ?: cause.toString()
    }

    constructor(
        errorCode: Int,
        errorName: String,
        errorText: String,
        cause: Throwable,
    ) : super("Google Play Integrity Error", cause) {
        metadata["code"] = errorCode
        metadata["name"] = errorName
        metadata["text"] = errorText
    }

    fun getName(): String =
        metadata["name"].toString()

    fun getCode(): Int? =
        metadata["code"]?.toString()?.toInt()

    fun getText(): String =
        metadata["text"].toString()

    fun getCause(): String? =
        metadata["cause"]?.toString()
}
