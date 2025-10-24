package io.muun.apollo.data.libwallet

import app_provided_data.KeyData
import io.muun.apollo.data.preferences.KeysRepository
import javax.inject.Inject

class KeyProvider @Inject constructor(
    private val keysRepository: KeysRepository,
): app_provided_data.KeyProvider {

    override fun fetchUserKey(): KeyData {
        val userKey = keysRepository.basePrivateKey.toBlocking().first()

        val keyData = KeyData()
        keyData.serialized = userKey.serializeBase58()
        keyData.path = userKey.absoluteDerivationPath
        return keyData
    }

    override fun fetchMuunKey(): KeyData {
        val muunKey = keysRepository.baseMuunPublicKey

        val keyData = KeyData()
        keyData.serialized = muunKey.serializeBase58()
        keyData.path = muunKey.absoluteDerivationPath
        return keyData
    }

    override fun fetchEncryptedMuunPrivateKey(): String {
        return keysRepository.encryptedMuunPrivateKey.toBlocking().first()
    }

    override fun fetchMaxDerivedIndex(): Long {
        return keysRepository.maxWatchingExternalAddressIndex.toLong()
    }
}
