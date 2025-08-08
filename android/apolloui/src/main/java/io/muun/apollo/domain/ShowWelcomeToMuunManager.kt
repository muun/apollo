package io.muun.apollo.domain

import io.muun.apollo.data.preferences.UserRepository
import javax.inject.Inject

class ShowWelcomeToMuunManager @Inject constructor(
    private val userRepository: UserRepository,
) {

    fun getSeen(): Boolean =
        userRepository.welcomeToMuunDialogSeen

    fun setSeen() {
        userRepository.setWelcomeToMuunDialogSeen()
    }
}