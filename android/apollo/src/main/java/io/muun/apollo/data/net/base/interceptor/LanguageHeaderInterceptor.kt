package io.muun.apollo.data.net.base.interceptor

import android.content.Context
import io.muun.apollo.data.net.base.BaseInterceptor
import io.muun.apollo.domain.utils.locale
import io.muun.common.net.HeaderUtils
import okhttp3.Request
import javax.inject.Inject

class LanguageHeaderInterceptor @Inject constructor(
    private val applicationContext: Context,
) : BaseInterceptor() {

    override fun processRequest(request: Request): Request {
        val language = applicationContext.locale().language
        return request.newBuilder()
            .addHeader(
                HeaderUtils.CLIENT_LANGUAGE,
                language.ifEmpty { HeaderUtils.DEFAULT_LANGUAGE_VALUE }
            )
            .build()
    }
}