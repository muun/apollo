package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.libwallet.LibwalletBridge
import io.muun.apollo.domain.model.EmergencyKitExport
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import rx.Observable
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenderEmergencyKitAction @Inject constructor(
    private val userRepository: UserRepository,
    private val keysRepository: KeysRepository,
    private val reportEmergencyKitExported: ReportEmergencyKitExportedAction,
    private val transformerFactory: ExecutionTransformerFactory,
) : BaseAsyncAction0<GeneratedEmergencyKit>() {

    inner class RequiredData(
        val userKey: String,
        val userFingerprint: String,
        val muunKey: String,
        val muunFingerprint: String,
    )

    /**
     * Prepare the emergency kit for export, and render the HTML.
     */
    override fun action(): Observable<GeneratedEmergencyKit> =
        Observable.defer {
            watchData().first()
                .map { renderSave(it) }
                .doOnNext { ek ->
                    val export = EmergencyKitExport(ek, false, EmergencyKitExport.Method.UNKNOWN)

                    // NOTE:
                    // Rather than use `run()`, we subscribe to this action() in background to avoid
                    // competing with other callers for the Action concurrency check.
                    // Remember: this is a fire-and-forget call
                    reportEmergencyKitExported.action(export)
                        .subscribeOn(transformerFactory.backgroundScheduler)
                        .subscribe({}, { error ->
                            Timber.i("Error while reportEmergencyKitExported")
                            Timber.e(error)
                        })
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

        userRepository.storeEmergencyKitVerificationCode(kitGen.verificationCode)

        return kitGen
    }

    private fun watchData() = Observable.zip(
        keysRepository.encryptedBasePrivateKey,
        keysRepository.userKeyFingerprint,
        keysRepository.encryptedMuunPrivateKey,
        keysRepository.muunKeyFingerprint,
        ::RequiredData
    )
}
