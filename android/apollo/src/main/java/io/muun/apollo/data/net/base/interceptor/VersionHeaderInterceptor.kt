package io.muun.apollo.data.net.base.interceptor

import android.os.Build
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.net.base.BaseInterceptor
import io.muun.apollo.data.preferences.ClientVersionRepository
import io.muun.common.api.ClientTypeJson
import io.muun.common.net.HeaderUtils
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

class VersionHeaderInterceptor @Inject constructor(
    private val clientVersionRepository: ClientVersionRepository,
) : BaseInterceptor() {

    override fun processRequest(originalRequest: Request): Request {
        return originalRequest.newBuilder()
            .addHeader(HeaderUtils.CLIENT_VERSION, "" + Globals.INSTANCE.versionCode)
            .addHeader(HeaderUtils.CLIENT_VERSION_NAME, "" + Globals.INSTANCE.versionName)
            .addHeader(HeaderUtils.CLIENT_TYPE, ClientTypeJson.APOLLO.toString())
            .addHeader(HeaderUtils.CLIENT_SDK_VERSION, "" + Build.VERSION.SDK_INT)
            .build()
    }

    override fun processResponse(originalResponse: Response): Response {
        val versionHeader = originalResponse.header(HeaderUtils.MIN_CLIENT_VERSION)

        HeaderUtils.getMinVersionFromHeader(versionHeader)
            .ifPresent { minClientVersion: Int ->
                clientVersionRepository.storeMinClientVersion(minClientVersion)
            }

        return super.processResponse(originalResponse)
    }
}