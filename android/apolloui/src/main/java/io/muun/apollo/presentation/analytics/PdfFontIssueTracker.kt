package io.muun.apollo.presentation.analytics

import android.content.Context
import android.os.Build
import io.muun.apollo.presentation.analytics.AnalyticsEvent.E_PDF_FONT_ISSUE
import io.muun.apollo.presentation.app.GlobalsImpl
import io.muun.apollo.presentation.ui.utils.hasAppInstalled
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
        var webViewVersion = "UNINSTALLED"
        try {
            val packageInfo = context.packageManager.getPackageInfo(WEBVIEW_PACKAGE_NAME, 0)
            webViewVersion = packageInfo.versionName
        } catch (e: Exception) {
            // do nothing
        }

        var chromeVersion = "UNINSTALLED"
        try {
            val packageInfo = context.packageManager.getPackageInfo(CHROME_PACKAGE_NAME, 0)
            chromeVersion = packageInfo.versionName
        } catch (e: Exception) {
            // do nothing
        }

        if (PDF_FONT_ISSUE_DEVICES.contains(GlobalsImpl.INSTANCE.deviceName)
            && PDF_FONT_ISSUE_WEB_VIEW_VERSIONS.contains(webViewVersion)
        ) {
            val analyticsEvent = E_PDF_FONT_ISSUE(
                event,
                GlobalsImpl.INSTANCE.deviceName,
                webViewVersion,
                chromeVersion,
                Build.VERSION.SDK_INT.toString()
            )
            analytics.report(analyticsEvent)
            Timber.w(analyticsEvent.toString())
        }
    }
}