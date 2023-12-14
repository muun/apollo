package io.muun.apollo.data.analytics

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.domain.model.user.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsProvider @Inject constructor(val context: Context) {

    private val fba = FirebaseAnalytics.getInstance(context)

    // Just for enriching error logs. A best effort to add metadata
    private val inMemoryMapBreadcrumbCollector = sortedMapOf<Long, Bundle>()

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

    fun attachAnalyticsMetadata(report: CrashReport) {
        report.metadata["breadcrumbs"] = getBreadcrumbMetadata()
        report.metadata["displayMetrics"] = getDisplayMetricsMetadata()
    }

    // PRIVATE STUFF

    private fun actuallyReport(event: AnalyticsEvent) {
        val bundle = Bundle().apply {
            putString("eventName", event.eventId)
            event.metadata.forEach { putString(it.key, it.value.toString()) }
        }

        fba.logEvent(event.eventId, bundle)
        inMemoryMapBreadcrumbCollector[System.currentTimeMillis()] = bundle
        Log.i("AnalyticsProvider", event.toString())
    }

    private fun getBreadcrumbMetadata(): String {
        val builder = StringBuilder()
        builder.append(" {\n")

        val breadcrumbs: Map<Long, Bundle> = inMemoryMapBreadcrumbCollector

        for (key in breadcrumbs.keys) {
            val bundle = breadcrumbs.getValue(key)
            val eventId = bundle["eventName"]
            builder.append("\t$eventId={ ${serializeBundle(bundle)} }\n")
        }

        builder.append("}\n")
        return builder.toString()
    }

    private fun getDisplayMetricsMetadata(): String {
        val displayMetrics = Resources.getSystem().displayMetrics

        val bundle = Bundle()
        bundle.putInt("height", displayMetrics.heightPixels)
        bundle.putInt("width", displayMetrics.widthPixels)
        bundle.putFloat("density", displayMetrics.scaledDensity)

        return " {\n\t${serializeBundle(bundle)}\n}"
    }

    private fun serializeBundle(bundle: Bundle): String {
        val builder = StringBuilder()
        var first = true

        for (param in bundle.keySet()) {

            if (param == "eventName") {
                continue // this is actually the breadcrumb key, let's avoid redundant info
            }

            if (!first) {
                builder.append(", ")
            }

            builder.append("$param=%${bundle[param]}")
            first = false
        }

        return builder.toString()
    }
}