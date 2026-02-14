package io.muun.apollo.data.analytics

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.errors.ReportAnalyticError
import io.muun.apollo.domain.model.report.ErrorReport
import io.muun.apollo.domain.model.user.User
import rx.Single
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsProvider @Inject constructor(context: Context) {

    private val fba = FirebaseAnalytics.getInstance(context)

    // Just for enriching error logs. A best effort to add metadata
    private val inMemoryMapBreadcrumbCollector = sortedMapOf<Long, Bundle>()

    fun loadBigQueryPseudoId(): Single<String?> =
        Single.fromEmitter<String> { emitter ->
            fba.appInstanceId
                .addOnSuccessListener { id: String? ->
                    // id can be null on platforms without google play services.
                    Timber.d("Loaded BigQueryPseudoId: $id")
                    emitter.onSuccess(id)
                }
                .addOnFailureListener { error ->
                    emitter.onError(error)
                }
        }

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

            // Avoid recursion (Timber.i reports a breadcrumb). TODO proper design and fix this
            if (event !is AnalyticsEvent.E_BREADCRUMB) {
                Timber.i("AnalyticsProvider: $event")
            }

        } catch (t: Throwable) {
            try {
                // FIXME: Desperate times require desperate solutions, currently Crashlytics object
                // doesn't expose a plain log, it records an analytics event too, we will address this later.
                FirebaseCrashlytics.getInstance().log("AnalyticsProvider: Failed processing analytics event ${event.eventId}, cause: ${t.message}")
                val bundle = Bundle().apply { putString("event", event.eventId) }
                fba.logEvent("e_tracking_error", bundle)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(ReportAnalyticError("AnalyticsProvider: Failed processing analytics event e_tracking_error, cause: ${e.message}"))
            }
        }
    }

    fun attachAnalyticsMetadata(report: ErrorReport) {
        report.metadata["breadcrumbs"] = getBreadcrumbMetadata()
        report.metadata["displayMetrics"] = getDisplayMetricsMetadata()
    }

    // PRIVATE STUFF

    private fun actuallyReport(event: AnalyticsEvent) {
        val bundle = Bundle().apply {
            putString("eventName", event.eventId)
            event.metadata.forEach {
                putString(it.key, AnalyticsEvent.safelyTrimParamValue(it.value.toString()))
            }
        }

        fba.logEvent(event.eventId, bundle)
        inMemoryMapBreadcrumbCollector[System.currentTimeMillis()] = bundle
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