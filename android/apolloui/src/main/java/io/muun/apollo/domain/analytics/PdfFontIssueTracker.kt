package io.muun.apollo.domain.analytics

import android.content.Context
import io.muun.apollo.data.afs.EarlyMetricsProvider
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_PDF_FONT_ISSUE
import timber.log.Timber

/**
 * This class purpose is to track and gain more data on the occurrences of a nasty  issue we've seen
 * in certain versions of the Android System WebView, Google Chrome and certain device models.
 *
 * This builds heavily on:
 * https://developer.android.com/training/package-visibility
 * https://medium.com/androiddevelopers/package-visibility-in-android-11-cc857f221cd9
 * https://stackoverflow.com/questions/41234678/determine-webview-implementation-system-webview-or-chrome
 */
class PdfFontIssueTracker constructor(
    private val context: Context,
    private val analytics: Analytics,
) {

    private val PDF_FONT_ISSUE_DEVICES = listOf("OnePlus6", "OnePlus6T")

    private val PDF_FONT_ISSUE_WEB_VIEW_VERSIONS = listOf("102.0.5005.125")

    private val WEBVIEW_PACKAGE_NAME = "com.google.android.webview"

    private val CHROME_PACKAGE_NAME = "com.android.chrome"


    fun track(event: AnalyticsEvent.PDF_FONT_ISSUE_TYPE) {
        val defaultVersion = "UNINSTALLED"
        var webViewVersion = defaultVersion
        try {
            val packageInfo = context.packageManager.getPackageInfo(WEBVIEW_PACKAGE_NAME, 0)
            webViewVersion = packageInfo?.versionName ?: defaultVersion
        } catch (e: Exception) {
            // do nothing
        }

        var chromeVersion = defaultVersion
        try {
            val packageInfo = context.packageManager.getPackageInfo(CHROME_PACKAGE_NAME, 0)
            chromeVersion = packageInfo?.versionName ?: defaultVersion
        } catch (e: Exception) {
            // do nothing
        }

        val earlyMetricsProvider = EarlyMetricsProvider(context)

        if (PDF_FONT_ISSUE_DEVICES.contains(earlyMetricsProvider.deviceName)
            && PDF_FONT_ISSUE_WEB_VIEW_VERSIONS.contains(webViewVersion)
        ) {
            val analyticsEvent = E_PDF_FONT_ISSUE(
                event,
                earlyMetricsProvider.deviceName,
                webViewVersion,
                chromeVersion,
                earlyMetricsProvider.sdkLevel.toString()
            )
            analytics.report(analyticsEvent)
            Timber.w(analyticsEvent.toString())
        }
    }
}