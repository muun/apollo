package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerifyEmergencyKitAction @Inject constructor(
    private val keysRepository: KeysRepository

): BaseAsyncAction1<String, Boolean>() {

    /**
     * Verify a given EK verification code matches expectations.
     */
    override fun action(verificationCode: String): Observable<Boolean> =
        Observable.defer {
            keysRepository
                .watchEmergencyKitVerificationCode()
                .first()
                .map {
                    checkNotNull(it)
                    (it == verificationCode)
                }
        }
}
