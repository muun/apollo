package io.muun.apollo.domain.analytics

import io.muun.apollo.data.analytics.AnalyticsProvider
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.model.user.User
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class Analytics @Inject constructor(val analyticsProvider: AnalyticsProvider) {

    /**
     * Set the user's properties, to be used by Analytics.
     */
    fun setUserProperties(user: User) {
        analyticsProvider.setUserProperties(user)
    }

    fun resetUserProperties() {
        analyticsProvider.resetUserProperties()
    }

    fun attachAnalyticsMetadata(report: CrashReport) {
        analyticsProvider.attachAnalyticsMetadata(report)
    }

    /**
     * Report an AnalyticsEvent.
     */
    fun report(event: AnalyticsEvent) {
        analyticsProvider.report(event)
    }
}