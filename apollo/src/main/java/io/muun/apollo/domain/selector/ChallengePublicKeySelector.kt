package io.muun.apollo.domain.selector

import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.common.crypto.ChallengeType
import javax.inject.Inject


open class ChallengePublicKeySelector @Inject constructor(
    private val keysRepository: KeysRepository
) {

    @VisibleForTesting // open so mockito can mock/spy
    open fun exists(type: ChallengeType) =
        keysRepository.hasChallengePublicKey(type)

    fun existsAnyType() =
        exists(ChallengeType.PASSWORD) || exists(ChallengeType.RECOVERY_CODE)
}