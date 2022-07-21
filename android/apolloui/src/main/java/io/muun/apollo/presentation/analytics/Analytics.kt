package io.muun.apollo.presentation.analytics

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.app.di.PerApplication
import timber.log.Timber
import javax.inject.Inject


@PerApplication
class Analytics @Inject constructor(val context: Context) {

    private val fba = FirebaseAnalytics.getInstance(context)

    // Just for enriching error logs. A best effort to add metadata
    private val inMemoryMapBreadcrumbCollector = mutableMapOf<String, Bundle>()

    /**
     * Set the user's properties, to be used by Analytics.
     */
    fun setUserProperties(user: User) {
        fba.setUserId(user.hid.toString())
        fba.setUserProperty("currency", user.unsafeGetPrimaryCurrency().currencyCode)
    }

    fun resetUserProperties() {
        fba.setUserId(null)
        fba.setUserProperty("email", null)
    }

    fun attachAnalyticsMetadata(report: CrashReport) {
        report.metadata["breadcrumbs"] = getBreadcrumbMetadata()
    }

    private fun getBreadcrumbMetadata(): String {
        val builder = StringBuilder()
        builder.append(" {\n")

        val breadcrumbs: Map<String, Bundle> = inMemoryMapBreadcrumbCollector

        for (eventId in breadcrumbs.keys) {
            builder.append("\t$eventId={ ${getBreadcrumb(breadcrumbs.getValue(eventId))} }\n")
        }

        builder.append("}\n")
        return builder.toString()
    }

    private fun getBreadcrumb(bundle: Bundle): String {
        val builder = StringBuilder()
        var first = true

        for (param in bundle.keySet()) {

            if (!first) {
                builder.append(", ")
            }

            builder.append("$param=%${bundle[param]}")
            first = false
        }

        return builder.toString()
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
        inMemoryMapBreadcrumbCollector[event.eventId] = bundle
        Timber.i(event.toString())
    }

}