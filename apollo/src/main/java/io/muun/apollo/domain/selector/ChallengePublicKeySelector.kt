package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.utils.onTypedErrorReturn
import io.muun.common.Optional
import io.muun.common.crypto.ChallengeType
import javax.inject.Inject


open class ChallengePublicKeySelector @Inject constructor(
    private val keysRepository: KeysRepository
) {

    fun getAsync(type: ChallengeType) =
        keysRepository.getChallengePublicKey(type)

    fun getAsyncOptional(type: ChallengeType) =
        getAsync(type)
            .map { Optional.of(it) }
            .onTypedErrorReturn(NoSuchElementException::class.java) { Optional.empty() }

    open fun exists(type: ChallengeType) =
        keysRepository.hasChallengePublicKey(type)

    fun existsAnyType() =
        exists(ChallengeType.PASSWORD) || exists(ChallengeType.RECOVERY_CODE)
}