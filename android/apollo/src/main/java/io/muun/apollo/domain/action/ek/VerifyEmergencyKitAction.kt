package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.errors.EmergencyKitInvalidCodeError
import io.muun.apollo.domain.errors.EmergencyKitOldCodeError
import io.muun.apollo.domain.model.EmergencyKitExport
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerifyEmergencyKitAction @Inject constructor(
    private val userRepository: UserRepository,
    private val reportEmergencyKitExported: ReportEmergencyKitExportedAction
): BaseAsyncAction2<String, GeneratedEmergencyKit, Void>() {

    /**
     * Verify a given EK verification code matches expectations.
     */
    override fun action(providedCode: String, kitGen: GeneratedEmergencyKit): Observable<Void> =
        Observable.fromCallable {

            val storedCodes = userRepository.fetchOne().emergencyKitVerificationCodes

            val newestCode = storedCodes.getNewest()
            checkNotNull(newestCode)

            if (providedCode == newestCode) {
                // Success! Nothing to do.
                return@fromCallable null

            } else if (storedCodes.containsOld(providedCode)) {
                // It's an old code. Raise an error with a hint using the first 2 chars:
                throw EmergencyKitOldCodeError(newestCode.take(2))

            } else {
                // Not even an old code, just plain invalid:
                throw EmergencyKitInvalidCodeError()
            }
        }
        .flatMap {
            reportEmergencyKitExported.actionNow(
                EmergencyKitExport(kitGen, true, EmergencyKitExport.Method.MANUAL)
            )

            Observable.just(null)
        }
}
