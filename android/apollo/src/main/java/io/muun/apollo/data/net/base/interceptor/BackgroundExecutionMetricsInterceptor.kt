package io.muun.apollo.data.net.base.interceptor

import io.muun.apollo.data.net.base.BaseInterceptor
import io.muun.apollo.data.os.BackgroundExecutionMetricsProvider
import io.muun.common.net.HeaderUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import javax.inject.Inject

class BackgroundExecutionMetricsInterceptor @Inject constructor(
    private val bemProvider: BackgroundExecutionMetricsProvider,
) : BaseInterceptor() {

    override fun processRequest(originalRequest: Request): Request {
        return originalRequest.newBuilder()
            .addHeader(
                HeaderUtils.BACKGROUND_EXECUTION_METRICS,
                Json.encodeToString(bemProvider.run())
            ).build()
    }
}