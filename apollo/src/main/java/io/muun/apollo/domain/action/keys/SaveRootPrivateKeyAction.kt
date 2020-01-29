package io.muun.apollo.domain.action.keys

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.Schema
import io.muun.common.crypto.hd.exception.KeyDerivationException
import rx.Observable
import javax.inject.Inject

class SaveRootPrivateKeyAction @Inject constructor(
    private val keysRepository: KeysRepository
): BaseAsyncAction1<PrivateKey, Void>() {

    /**
     * Store the root private key in a secure local storage.
     */
    override fun action(rootPrivateKey: PrivateKey) =
        Observable.defer<Void> {
            try {
                val basePrivateKey = rootPrivateKey.deriveFromAbsolutePath(Schema.getBasePath())
                keysRepository.storeBasePrivateKey(basePrivateKey)

            } catch (e: KeyDerivationException) {
                Observable.error(RuntimeException(e)) // This should not happen
            }
        }
}