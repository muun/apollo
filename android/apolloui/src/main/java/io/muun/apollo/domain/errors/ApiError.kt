package io.muun.apollo.domain.errors

import io.muun.common.exception.HttpException


class ApiError(cause: HttpException) : MuunError(cause) {

    override val classification = ErrorClassification.UNEXPECTED

    init {
        metadata["errorCode"] = cause.errorCode.name()
        metadata["requestId"] = cause.requestId ?: 0
        metadata["developerMessage"] = cause.developerMessage ?: "null"
    }

}