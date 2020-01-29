package io.muun.apollo.domain.action.keys

import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.errors.PasswordIntegrityError
import io.muun.common.crypto.hd.KeyCrypter
import rx.Observable
import javax.inject.Inject


class DecryptRootPrivateKeyAction @Inject constructor(
    private val saveRootPrivateKey: SaveRootPrivateKeyAction
): BaseAsyncAction2<String, String, Void>() {

    override fun action(encryptedKey: String, userInput: String) =
        Observable.defer {
            val rootPrivateKey = KeyCrypter().decrypt(encryptedKey, userInput)

            if (rootPrivateKey.isPresent) {
                saveRootPrivateKey.action(rootPrivateKey.get())
            } else {
                throw PasswordIntegrityError()
            }
        }
}

