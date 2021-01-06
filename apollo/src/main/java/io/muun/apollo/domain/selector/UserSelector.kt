package io.muun.apollo.domain.selector

import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.User
import io.muun.common.Optional
import rx.Observable
import javax.inject.Inject

open class UserSelector @Inject constructor(private val userRepository: UserRepository) {

    fun watch(): Observable<User> =
        userRepository.fetch()

    private fun watchOptional(): Observable<Optional<User>> =
        userRepository.fetchOptional()

    @VisibleForTesting // open so mockito can mock/spy
    open fun get(): User =
        watch().toBlocking().first()

    fun getOptional(): Optional<User> =
        watchOptional().toBlocking().first()

    fun emailSetupSkipped(): Boolean =
        userRepository.emailSetupSkipped

    fun skipEmailSetup() =
        userRepository.setEmailSetupSkipped()

    fun watchBalanceHidden(): Observable<Boolean> =
        userRepository.watchBalanceHidden()

    fun setBalanceHidden(hidden: Boolean) {
        userRepository.setBalanceHidden(hidden)
    }
}