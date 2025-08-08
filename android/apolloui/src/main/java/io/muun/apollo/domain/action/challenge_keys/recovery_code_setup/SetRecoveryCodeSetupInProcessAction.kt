package io.muun.apollo.domain.action.challenge_keys.recovery_code_setup

import io.muun.apollo.data.preferences.UserRepository
import javax.inject.Inject

class SetRecoveryCodeSetupInProcessAction @Inject constructor(
    private val userRepository: UserRepository
) {

    fun run(inProcess: Boolean) {
        return userRepository.setRecoveryCodeSetupInProcess(inProcess)
    }
}