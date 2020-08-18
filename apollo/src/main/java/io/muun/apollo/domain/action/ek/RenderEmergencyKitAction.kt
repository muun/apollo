package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.libwallet.LibwalletBridge
import rx.Observable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenderEmergencyKitAction @Inject constructor(
    private val keysRepository: KeysRepository

): BaseAsyncAction0<String>() {

    /**
     * Prepare the emergency kit for export, and render the HTML.
     */
    override fun action() =
        Observable.defer {
            watchKeys().first().map { renderSave(it.first, it.second) }
        }

    private fun renderSave(userKey: String, muunKey: String): String {
        val kitGen = LibwalletBridge.generateEmergencyKit(userKey, muunKey, Locale.getDefault())

        keysRepository.storeEmergencyKitVerificationCode(kitGen.verificationCode)
        return kitGen.html
    }

    private fun watchKeys() =
        Observable.zip(
            keysRepository.encryptedBasePrivateKey,
            keysRepository.encryptedMuunPrivateKey,
            { userKey, muunKey -> Pair(userKey, muunKey) }
        )
}
