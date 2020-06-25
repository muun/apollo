package io.muun.apollo.domain.action.user

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.keys.CreateChallengeSetupAction
import io.muun.apollo.domain.utils.zipWith
import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.model.challenge.ChallengeSignature
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportKeysExportedAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userRepository: UserRepository

): BaseAsyncAction0<Void>() {

    /**
     * Tell Houston we have exported our keys.
     */
    override fun action() =
        houstonClient.reportKeysExported()
            .doOnNext {
                val user = userRepository.fetchOne()
                user.hasExportedKeys = true

                userRepository.store(user)
            }
}
