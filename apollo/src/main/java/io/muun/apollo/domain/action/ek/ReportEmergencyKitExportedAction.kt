package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.common.Optional
import io.muun.common.dates.MuunZonedDateTime
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

): BaseAsyncAction0<Void>() {

    /**
     * Tell Houston we have exported our keys.
     */
    override fun action() =
        Observable.defer {
            val user = userRepository.fetchOne()
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            // Store locally for immediate feedback:
            user.emergencyKitLastExportedAt = Optional.of(now);
            userRepository.store(user)

            val verificationCode = keysRepository.watchEmergencyKitVerificationCode()
                .toBlocking()
                .first()

            houstonClient.reportEmergencyKitExported(now, verificationCode)
        }
}
