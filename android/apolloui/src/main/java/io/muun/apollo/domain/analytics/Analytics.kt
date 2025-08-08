package io.muun.apollo.domain.analytics

import io.muun.apollo.data.analytics.AnalyticsProvider
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository
import io.muun.apollo.domain.model.report.ErrorReport
import io.muun.apollo.domain.model.user.User
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Analytics @Inject constructor(
    private val analyticsProvider: AnalyticsProvider,
    private val firebaseInstallationIdRepository: FirebaseInstallationIdRepository,
) {

    fun loadBigQueryPseudoId() {
        analyticsProvider.loadBigQueryPseudoId()
            .subscribe(
                // id can be null on platforms without google play services.
                { id -> id?.let { firebaseInstallationIdRepository.storeBigQueryPseudoId(id) } },
                { error -> Timber.e(error) }
            )
    }

    /**
     * Set the user's properties, to be used by Analytics.
     */
    fun setUserProperties(user: User) {
        analyticsProvider.setUserProperties(user)
    }

    fun resetUserProperties() {
        analyticsProvider.resetUserProperties()
    }

    fun attachAnalyticsMetadata(report: ErrorReport) {
        analyticsProvider.attachAnalyticsMetadata(report)
    }

    /**
     * Report an AnalyticsEvent.
     */
    fun report(event: AnalyticsEvent) {
        analyticsProvider.report(event)
    }
}