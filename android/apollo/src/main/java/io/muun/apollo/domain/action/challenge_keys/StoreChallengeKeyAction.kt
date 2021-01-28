package io.muun.apollo.domain.action.challenge_keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.model.User
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import rx.Observable
import timber.log.Timber
import javax.inject.Inject

open class StoreChallengeKeyAction @Inject constructor(
    private val keysRepository: KeysRepository,
    private val userRepository: UserRepository

): BaseAsyncAction2<ChallengeType, ChallengePublicKey, Void>() {

    /**
     * Store a ChallengeKey, updating data where needed.
     */
    override fun action(type: ChallengeType, publicKey: ChallengePublicKey) =
        Observable.fromCallable { storeKey(type, publicKey) }.toVoid()

    private fun storeKey(type: ChallengeType, publicKey: ChallengePublicKey) {
        keysRepository.storePublicChallengeKey(publicKey, type)

        userRepository.fetchOneOptional().ifPresent {
            updateUser(type, it)
        }
    }

    private fun updateUser(type: ChallengeType, user: User) {
        when (type) {
            ChallengeType.PASSWORD -> { user.hasPassword = true }
            ChallengeType.RECOVERY_CODE -> { user.hasRecoveryCode = true }
            else -> {
                /* nothing to set */
                Timber.e(IllegalArgumentException("Trying to store Invalid ChallengeKey: $type"))
            }
        }

        userRepository.store(user)
    }
}