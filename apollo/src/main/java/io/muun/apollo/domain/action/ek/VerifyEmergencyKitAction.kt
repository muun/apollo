package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.errors.EmergencyKitInvalidCodeError
import io.muun.apollo.domain.errors.EmergencyKitOldCodeError
import io.muun.apollo.domain.utils.toVoid
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerifyEmergencyKitAction @Inject constructor(
    private val keysRepository: KeysRepository

): BaseAsyncAction1<String, Void>() {

    /**
     * Verify a given EK verification code matches expectations.
     */
    override fun action(providedCode: String): Observable<Void> =
        Observable.defer {
            keysRepository
                .watchEmergencyKitVerificationCodes()
                .first()
                .doOnNext {
                    val newestCode = it.getNewest()
                    checkNotNull(newestCode)

                    if (providedCode == newestCode) {
                        // Success! Nothing to do.

                    } else if (it.containsOld(providedCode)) {
                        // It's an old code. Raise an error with a hint using the first 2 chars:
                        throw EmergencyKitOldCodeError(newestCode.take(2))

                    } else {
                        // Not even an old code, just plain invalid:
                        throw EmergencyKitInvalidCodeError()
                    }
                }
                .toVoid()
        }
}
