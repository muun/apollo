package io.muun.apollo.data.libwallet

import android.content.Context
import android.os.Build
import app_provided_data.Session
import io.muun.apollo.data.afs.BackgroundExecutionMetricsProvider
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.external.HoustonConfig
import io.muun.apollo.data.preferences.AuthRepository
import io.muun.apollo.data.preferences.ClientVersionRepository
import io.muun.apollo.data.toSafeAscii
import io.muun.apollo.domain.utils.locale
import io.muun.common.api.ClientTypeJson
import io.muun.common.model.SessionStatus
import io.muun.common.net.HeaderUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

class HttpClientSessionProvider @Inject constructor(
    private val authRepository: AuthRepository,
    private val clientVersionRepository: ClientVersionRepository,
    private val applicationContext: Context,
    private var houstonConfig: HoustonConfig,
    private val bemProvider: BackgroundExecutionMetricsProvider
): app_provided_data.HttpClientSessionProvider {

    override fun session(): Session {
        val language = applicationContext.locale().language

        val session = Session()
        val authToken = authRepository.serverJwt.orElse("")
        session.authToken = authToken
        session.clientType = ClientTypeJson.APOLLO.toString()
        session.clientVersion = Globals.INSTANCE.versionCode.toString()
        session.clientVersionName = Globals.INSTANCE.versionName
        session.clientSdkVersion = Build.VERSION.SDK_INT.toString()
        session.baseURL = houstonConfig.url
        session.language = language.ifEmpty { HeaderUtils.DEFAULT_LANGUAGE_VALUE }
        try {
            session.backgroundExecutionMetrics =
                Json.encodeToString(bemProvider.run()).toSafeAscii()
        } catch (e: Throwable) {
            Timber.e(e)
        }

        return session
    }

    override fun setSessionStatus(sessionStatus: String?) {
        sessionStatus
            ?.let(SessionStatus::valueOf)
            ?.let(authRepository::storeSessionStatus)
    }

    override fun setMinClientVersion(minClientVersion: String) {
        minClientVersion
            .toIntOrNull()
            ?.let(clientVersionRepository::storeMinClientVersion)
    }

    override fun setAuthToken(authToken: String) {
        if (authToken.isNotEmpty()) {
            authRepository.storeServerJwt(authToken)
        }
    }
}