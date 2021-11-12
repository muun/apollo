package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.model.EmergencyKitExport
import io.muun.apollo.domain.model.user.EmergencyKit
import io.muun.common.Optional
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportEmergencyKitExportedAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userRepository: UserRepository
): BaseAsyncAction1<EmergencyKitExport, Void>() {

    /**
     * Tell Houston we have exported our keys.
     */
    override fun action(export: EmergencyKitExport): Observable<Void> =
        Observable.defer {
            val user = userRepository.fetchOne()

            if (export.isVerified) {
                // Store locally for immediate feedback:
                val emergencyKit = EmergencyKit(
                    export.exportedAt,
                    export.generatedKit.version,
                    export.method
                )

                user.emergencyKit = Optional.of(emergencyKit)
                user.emergencyKitVersions.add(emergencyKit.version)
                userRepository.store(user)
            }

            houstonClient.reportEmergencyKitExported(export)
        }
}
