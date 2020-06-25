package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.utils.onTypedErrorResumeNext
import io.muun.apollo.domain.utils.onTypedErrorReturn
import io.muun.common.Optional
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
}