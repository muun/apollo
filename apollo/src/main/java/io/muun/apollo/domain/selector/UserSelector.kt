package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.UserRepository
import javax.inject.Inject

open class UserSelector @Inject constructor(private val userRepository: UserRepository) {

    fun watch() =
        userRepository.fetch()

    fun watchOptional() =
        userRepository.fetchOptional()

    open fun get() =
        watch().toBlocking().first()

    fun getOptional() =
        watchOptional().toBlocking().first()

    fun getFcmToken() =
        watchFcmToken().toBlocking().first()

    fun watchFcmToken() =
        userRepository.watchFcmToken()
}