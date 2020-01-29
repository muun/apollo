package io.muun.apollo.domain.action.keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.common.crypto.hd.KeyCrypter
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.Schema
import io.muun.common.crypto.hd.exception.KeyDerivationException
import rx.Observable
import javax.inject.Inject

class CreateRootPrivateKeyAction @Inject constructor(
    private val keysRepository: KeysRepository,
    private val saveRootPrivateKey: SaveRootPrivateKeyAction
): BaseAsyncAction1<String, String>() {

    /**
     * Creates a local pair of keys, which can derive everything in our schema.
     *
     * @return the encrypted root private key.
     */
    override fun action(passphrase: String): Observable<String> {
        while (true) {
            try {
                val rootPrivateKey: PrivateKey = keysRepository.createRootPrivateKey()

                // Check if we can derive all the subtree keys
                for (subtreePath in Schema.getAllSubtreePaths()) {
                    rootPrivateKey.deriveFromAbsolutePath(subtreePath)
                }

                return saveRootPrivateKey.action(rootPrivateKey)
                    .map { KeyCrypter().encrypt(rootPrivateKey, passphrase) }

            } catch (e: KeyDerivationException) {
                // Sadly, not all keys are derivable at all paths. There's always a (really) small
                // chance that the derivation will fail, so we have to make sure that the generated
                // private key is derivable at our base paths. If it isn't, then we just try with
                // another root key.
            }
        }
    }

}