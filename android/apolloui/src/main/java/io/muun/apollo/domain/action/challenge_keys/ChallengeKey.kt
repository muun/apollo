package io.muun.apollo.domain.action.challenge_keys

import io.muun.common.crypto.ChallengePrivateKey
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.utils.Encodings
import libwallet.Libwallet

object ChallengeKey {

    fun buildPublic(type: ChallengeType, secret: String, salt: ByteArray?): ChallengePublicKey =
        if (type == ChallengeType.RECOVERY_CODE) {

            val challengePrivateKey = Libwallet.recoveryCodeToKey(secret, null)
            val pubKeyBytes = Encodings.hexToBytes(challengePrivateKey.pubKeyHex())

            // We're using version 2 here, since this method and the returning ChallengePublicKey
            // will ultimately be used ONLY for RC V2. If we're planning on using this for RC V1, we
            // should refactor this and Libwallet.recoveryCodeToKey() to distinguish RC version from
            // the secret/inputText (libwallet already does this, but does not expose the version).
            ChallengePublicKey(pubKeyBytes, salt, 2)

        } else {
            ChallengePrivateKey.fromUserInput(secret, salt, 1).challengePublicKey
        }
}