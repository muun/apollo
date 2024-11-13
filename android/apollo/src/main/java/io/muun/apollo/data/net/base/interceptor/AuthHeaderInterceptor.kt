package io.muun.apollo.data.net.base.interceptor

import io.muun.apollo.data.net.base.BaseInterceptor
import io.muun.apollo.data.preferences.AuthRepository
import io.muun.common.model.SessionStatus
import io.muun.common.net.HeaderUtils
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject

class AuthHeaderInterceptor @Inject constructor(
    private val authRepository: AuthRepository,
) : BaseInterceptor() {

    override fun processRequest(originalRequest: Request): Request {
        // Attach the request header if a token is available:
        val serverJwt = authRepository.serverJwt.orElse(null)
        return if (serverJwt != null) {
            originalRequest.newBuilder()
                .addHeader(HeaderUtils.AUTHORIZATION, "Bearer $serverJwt")
                .build()

        } else {
            originalRequest
        }
    }

    override fun processResponse(originalResponse: Response): Response {
        // Save the token in the response header if one is found...
        val authHeaderValue = originalResponse.header(HeaderUtils.AUTHORIZATION)
        val requestAuthHeaderValue = originalResponse.request().header(HeaderUtils.AUTHORIZATION)

        // But avoid redundant jwt saves (involves writing to keystore) if we can
        if (authHeaderValue != requestAuthHeaderValue) {
            HeaderUtils.getBearerTokenFromHeader(authHeaderValue)
                .ifPresent { serverJwt: String ->
                    authRepository.storeServerJwt(serverJwt)
                }
        }

        // We need a reliable way (across all envs: local, CI, prd, etc...) to identify the logout
        // requests. We could inject HoustonConfig and build the entire URL (minus port)
        // or... we can do this :)
        val url = originalResponse.request().url().url().toString()
        val isLogout = url.endsWith("sessions/logout")
        val isDelete = url.endsWith("user/delete")

        if (!isLogout && !isDelete) {
            val sessionStatusHeaderValue = originalResponse.header(HeaderUtils.SESSION_STATUS)
            HeaderUtils.getSessionStatusFromHeader(sessionStatusHeaderValue)
                .ifPresent { sessionStatus: SessionStatus ->
                    authRepository.storeSessionStatus(sessionStatus)
                }
        }
        return originalResponse
    }
}