package io.muun.apollo.data.net.base.interceptor

import io.muun.apollo.data.logging.LoggingRequestTracker.reportRecentRequest
import io.muun.apollo.data.net.base.BaseInterceptor
import io.muun.apollo.data.net.base.CallAdapterFactory
import io.muun.common.net.HeaderUtils
import okhttp3.Request
import javax.inject.Inject

class IdempotencyKeyInterceptor @Inject constructor(
    private val provider: CallAdapterFactory,
) : BaseInterceptor() {

    override fun processRequest(originalRequest: Request): Request {
        val idempotencyKey = provider.idempotencyKey
            ?: return originalRequest // TODO find out why, when how, can this happen

        reportRequest(idempotencyKey, originalRequest.url().toString())

        return originalRequest.newBuilder()
            .addHeader(HeaderUtils.IDEMPOTENCY_KEY, idempotencyKey)
            .build()
    }

    private fun reportRequest(idempotencyKey: String, url: String) {
        reportRecentRequest(idempotencyKey, url)
    }
}