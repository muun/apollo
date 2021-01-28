package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.common.Optional
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportEmergencyKitExportedAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userRepository: UserRepository,
    private val keysRepository: KeysRepository

): BaseAsyncAction1<Boolean, Void>() {

    /**
     * Tell Houston we have exported our keys.
     */
    override fun action(verified: Boolean): Observable<Void> =
        Observable.defer {
            val user = userRepository.fetchOne()
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            if (verified) {
                // Store locally for immediate feedback:
                user.emergencyKitLastExportedAt = Optional.of(now);
                userRepository.store(user)
            }

            val verificationCode = keysRepository.watchEmergencyKitVerificationCodes()
                .map { it.getNewest() }
                .toBlocking()
                .first()

            houstonClient.reportEmergencyKitExported(now, verified, verificationCode)
        }
}
