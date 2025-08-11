package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.AuthRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.common.model.SessionStatus
import rx.Observable
import javax.inject.Inject


class LoginAuthorizedSelector @Inject constructor(
    val authRepository: AuthRepository,
    val userRepository: UserRepository
) {

    fun watch(targetStatus: SessionStatus): Observable<Boolean> =
        authRepository.watchSessionStatus()
            .map { it.orElse(null) }
            .map { it == targetStatus }
}