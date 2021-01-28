package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.libwallet.LibwalletBridge
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import rx.Observable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenderEmergencyKitAction @Inject constructor(
    private val keysRepository: KeysRepository,
    private val reportEmergencyKitExported: ReportEmergencyKitExportedAction

): BaseAsyncAction0<GeneratedEmergencyKit>() {

    inner class RequiredData(
        val userKey: String,
        val userFingerprint: String,
        val muunKey: String,
        val muunFingerprint: String
    )

    /**
     * Prepare the emergency kit for export, and render the HTML.
     */
    override fun action(): Observable<GeneratedEmergencyKit> =
        Observable.defer {
            watchData().first()
                .map { renderSave(it) }
                .doOnNext {
                    reportEmergencyKitExported.run(false) // fire and forget
                }
        }

    private fun renderSave(data: RequiredData): GeneratedEmergencyKit {
        val kitGen = LibwalletBridge.generateEmergencyKit(
            data.userKey,
            data.userFingerprint,
            data.muunKey,
            data.muunFingerprint,
            Locale.getDefault()
        )

        keysRepository.storeEmergencyKitVerificationCode(kitGen.verificationCode)

        return kitGen
    }

    private fun watchData() =
        Observable.zip(
            keysRepository.encryptedBasePrivateKey,
            keysRepository.userKeyFingerprint,
            keysRepository.encryptedMuunPrivateKey,
            keysRepository.muunKeyFingerprint,
            ::RequiredData
        )
}
