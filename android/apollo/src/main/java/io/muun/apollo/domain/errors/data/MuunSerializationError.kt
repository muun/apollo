package io.muun.apollo.domain.errors.data

import io.muun.apollo.domain.errors.MuunError
import io.muun.common.exception.PotentialBug
import okhttp3.Request

class MuunSerializationError(
    supportId: String,
    originalRequest: Request,
    cause: Throwable,
) : MuunError(cause), PotentialBug {

    init {
        metadata["supportId"] = supportId
        metadata["request"] = originalRequest.url().uri().toString()
    }
}