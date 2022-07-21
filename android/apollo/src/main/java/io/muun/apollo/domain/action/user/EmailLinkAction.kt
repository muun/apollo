package io.muun.apollo.domain.action.user

import io.muun.apollo.data.preferences.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
// open (non-final) class so mockito can mock/spy
open class EmailLinkAction @Inject constructor(private val userRepository: UserRepository) {

    fun setPending(emailLink: String) {
        userRepository.pendingEmailLink = emailLink;
    }

    fun getPending(): String {
        return userRepository.pendingEmailLink
    }
}