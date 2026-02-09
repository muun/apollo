package io.muun.apollo.domain.action.session

import io.muun.apollo.data.preferences.AuthRepository
import io.muun.apollo.data.preferences.BiometricsRepository
import io.muun.apollo.domain.action.LogoutActions
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.errors.MuunError
import io.muun.common.Optional
import timber.log.Timber
import javax.inject.Inject

class LogoutAction @Inject constructor(
    private val logoutActions: LogoutActions,
    private val userActions: UserActions,
    private val authRepository: AuthRepository,
    private val biometricsRepository: BiometricsRepository,
) {

    fun run() {
        val jwt: String = getJwt()
        logoutActions.destroyRecoverableWallet()
        userActions.notifyLogoutAction.run(jwt)
        biometricsRepository.deleteUserOptInBiometrics()
    }

    private fun getJwt(): String {
        val serverJwt: Optional<String> = authRepository.serverJwt
        if (!serverJwt.isPresent) {
            // Shouldn't happen but we wanna know 'cause probably a bug
            Timber.e(MuunError("Auth token expected to be present"))
            return ""
        }
        return serverJwt.get()
    }
}