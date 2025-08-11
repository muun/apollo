package io.muun.apollo.data.net.base.interceptor

import io.muun.apollo.data.net.base.BaseInterceptor
import io.muun.apollo.data.afs.BackgroundExecutionMetricsProvider
import io.muun.apollo.data.toSafeAscii
import io.muun.apollo.domain.errors.data.MuunSerializationError
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.selector.UserSelector
import io.muun.common.net.HeaderUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject

class BackgroundExecutionMetricsInterceptor @Inject constructor(
    private val bemProvider: BackgroundExecutionMetricsProvider,
    private val userSel: UserSelector,
) : BaseInterceptor() {

    override fun processRequest(originalRequest: Request): Request {
        return originalRequest.newBuilder()
            .addBem(originalRequest)
            .build()
    }

    private fun Request.Builder.addBem(originalRequest: Request): Request.Builder {
        val encodeToJson = safelyEncodeJson(originalRequest)

        return if (encodeToJson != null) {
            try {
                addHeader(HeaderUtils.BACKGROUND_EXECUTION_METRICS, encodeToJson)
            } catch (e: Throwable) {
                logError(originalRequest, e)
                this
            }
        } else {
            this
        }
    }

    private fun safelyEncodeJson(originalRequest: Request): String? {
        return try {
            Json.encodeToString(bemProvider.run()).toSafeAscii()
        } catch (e: Throwable) {
            logError(originalRequest, e)
            null
        }
    }

    private fun logError(originalRequest: Request, e: Throwable) {
        val supportId = userSel.getOptional()
            .flatMap { obj: User -> obj.supportId }
            .orElse("Not logged in")
        Timber.e(MuunSerializationError(supportId, originalRequest, e))
    }
}