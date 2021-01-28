package io.muun.apollo.presentation.analytics

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import io.muun.apollo.domain.model.User
import javax.inject.Inject


class Analytics @Inject constructor(val context: Context) {

    private val fba = FirebaseAnalytics.getInstance(context)

    /**
     * Set the user's properties, to be used by Analytics.
     */
    fun setUserProperties(user: User) {
        fba.setUserId(user.hid.toString())
        fba.setUserProperty("email", user.email.map(this::sanitizeEmail).orElse(null))
        fba.setUserProperty("currency", user.unsafeGetPrimaryCurrency().currencyCode)
    }

    fun resetUserProperties() {
        fba.setUserId(null)
        fba.setUserProperty("email", null)
    }

    /**
     * Report an AnalyticsEvent.
     */
    fun report(event: AnalyticsEvent) {
        try {
            actuallyReport(event)
        } catch (t: Throwable) {

            val bundle = Bundle().apply { putString("event", event.eventId) }
            fba.logEvent("e_tracking_error", bundle)
        }
    }

    private fun actuallyReport(event: AnalyticsEvent) {
        val bundle = Bundle().apply {
            event.metadata.forEach { putString(it.key, it.value.toString()) }
        }

        val displayMetrics = Resources.getSystem().displayMetrics

        bundle.putInt("height", displayMetrics.heightPixels)
        bundle.putInt("width", displayMetrics.widthPixels)
        bundle.putFloat("density", displayMetrics.scaledDensity)

        fba.logEvent(event.eventId, bundle)
    }

    private fun sanitizeEmail(email: String) =
        email.take(36)
}