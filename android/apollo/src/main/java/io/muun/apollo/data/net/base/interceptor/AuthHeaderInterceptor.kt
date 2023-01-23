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

    override fun processRequest(request: Request): Request {
        // Attach the request header if a token is available:
        val serverJwt = authRepository.serverJwt.orElse(null)
        return if (serverJwt != null) {
            request.newBuilder()
                .addHeader(HeaderUtils.AUTHORIZATION, "Bearer $serverJwt")
                .build()

        } else {
            request
        }
    }

    override fun processResponse(response: Response): Response {
        // Save the token in the response header if one is found
        val authHeaderValue = response.header(HeaderUtils.AUTHORIZATION)
        HeaderUtils.getBearerTokenFromHeader(authHeaderValue)
            .ifPresent { serverJwt: String -> authRepository.storeServerJwt(serverJwt) }

        // We need a reliable way (across all envs: local, CI, prd, etc...) to identify the logout
        // requests. We could inject HoustonConfig and build the entire URL (minus port)
        // or... we can do this :)
        val url = response.request().url().url().toString()
        val isLogout = url.endsWith("sessions/logout")

        if (!isLogout) {
            val sessionStatusHeaderValue = response.header(HeaderUtils.SESSION_STATUS)
            HeaderUtils.getSessionStatusFromHeader(sessionStatusHeaderValue)
                .ifPresent { sessionStatus: SessionStatus ->
                    authRepository.storeSessionStatus(sessionStatus)
                }
        }
        return response
    }
}