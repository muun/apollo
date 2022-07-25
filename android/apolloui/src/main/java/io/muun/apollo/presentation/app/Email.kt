package io.muun.apollo.presentation.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.muun.apollo.R
import io.muun.apollo.domain.model.report.EmailReport

object Email {

    /**
     * Check if there's an email app installed on the device (without actually resolving intent,
     * which can cause an Intent chooser from the OS to pop up).
     */
    fun hasEmailAppInstalled(context: Context): Boolean {
        val intent = getSendEmailIntent()
        val pm: PackageManager = context.packageManager
        val list = pm.queryIntentActivities(intent, 0)

        // This is to account for a weird case that happens on Android emulators
        // See: https://stackoverflow.com/a/31051791/901465
        val theOnlyAppInstalledIsAndroidFallback =
            list.size == 1 && list[0].activityInfo.packageName == "com.android.fallback"

        return list.size != 0 && !theOnlyAppInstalledIsAndroidFallback
    }

    /**
     * Build a generic Android Intent to open an Email app. Without further additions to the intent,
     * this intent will open an email app at their launch/home activity (e.g most likely the inbox).
     * This is intended to be used when trying to send a user to an email app to CHECK/OPEN an
     * email.
     * To open email app with the intention to SEND an email, please refer to getSendEmailIntent().
     */
    fun getEmailClientIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL)

    /**
     * Build an Android Intent to send an error report email to our support team.
     */
    fun buildEmailReportIntent(context: Context, emailReport: EmailReport): Intent {

        val subjectPrefix: String = context.getString(R.string.error_report_email_subject)
        val subject: String = emailReport.subject(subjectPrefix)
        val body: String = emailReport.body

        return composeEmail(arrayOf("support@muun.com"), subject, body)
    }

    /**
     * Build an email intent to our support team, without any pre-filled data.
     */
    fun composeSupportEmail(): Intent {
        val intent = getSendEmailIntent()
        intent.putExtra(Intent.EXTRA_EMAIL, "support@muun.com")
        return intent
    }

    /**
     * Build a send email intent, with no pre-filled data.
     * This is intended to be used when trying to prompt a user to SEND an email.
     * To open email app with the intention to to CHECK/OPEN an email, please refer to
     * getEmailClientIntent().
     */
    private fun getSendEmailIntent(): Intent {
        val intent = Intent(Intent.ACTION_SENDTO)
        return intent.setData(Uri.parse("mailto:")) // only email apps should handle this
    }

    /**
     * Correct way of opening email intent. According to android docs:
     * https://developer.android.com/guide/components/intents-common.html#Email
     */
    private fun composeEmail(addresses: Array<String>, subject: String, body: String): Intent {
        val intent = getSendEmailIntent()
        intent.putExtra(Intent.EXTRA_EMAIL, addresses)
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, body)
        return intent
    }
}