package io.muun.apollo.data.os.sharer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi


class FileSharer(val context: Context) {

    /**
     * The result of a choice in a share dialog.
     */
    class Selection(val className: String?)

    /**
     * The parameters to fill an email form via Intent.
     */
    class Email(
        val recipient: String? = null,
        val subject: String? = null,
        val body: String? = null,
        val attachment: Uri? = null,
        val attachmentType: String? = null
    )

    /**
     * Information about an installed application that can receive an Intent.
     */
    class Target(
        val label: String,
        val icon: Drawable,
        val component: ComponentName
    ) {
        val id = component.hashCode() // whatever, I need a number, this will do
    }

    companion object {
        /**
         * Function to report results from the share dialog. This could be much more sophisticated
         * (supporting an event queue, multiple listeners, etc) but the UI can't do concurrent shares
         * so it's not necessary.
         */
        var onSelectionListener = { _: Selection -> }
    }

    fun getEmailTargets(): List<Target> {
        // Create an Intent that only email applications can handle:
        val dummyEmailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))

        // Query the Package Manager for handlers:
        val pm = context.packageManager
        val intentResolvers = pm.queryIntentActivities(dummyEmailIntent, 0)

        // Create a list of viable Targets based on the results:
        return intentResolvers
            .filter {
                // This resolver appears only in Android emulators, but we can't ignore it
                // because it sabotages our UI testing:
                it.activityInfo.packageName != "com.android.fallback"
            }
            .map {
                Target(
                    it.loadLabel(pm).toString(),
                    it.loadIcon(pm),
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                )
            }
    }

    /**
     * Create an Intent to send an email with an attached file.
     */
    fun getEmailIntent(email: Email, target: Target): Intent {
        val intent = Intent(Intent.ACTION_SEND)
            .setComponent(target.component)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        email.recipient?.let { intent.putExtra(Intent.EXTRA_EMAIL, it) }
        email.subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        email.body?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        email.attachment?.let { intent.putExtra(Intent.EXTRA_STREAM, it) }
        email.attachmentType?.let { intent.setType(it) }

        return intent
    }

    /**
     * Create an Intent to share a file with an external application.
     */
    fun getShareIntent(fileUri: Uri, fileType: String): Intent {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            getShareIntentPre22(fileUri, fileType)
        } else {
            getShareIntentPost22(fileUri, fileType)
        }
    }

    private fun getShareIntentPre22(fileUri: Uri, fileType: String): Intent {
        // Before API 22, we can only rely on the system handling this intent. If no default app
        // is set, it should show the "Complete action with..." dialog. Not as rich, but it works.
        return Intent(Intent.ACTION_SEND)
            .setType(fileType)
            .putExtra(Intent.EXTRA_STREAM, fileUri)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun getShareIntentPost22(fileUri: Uri, fileType: String): Intent {
        // We're going to need several (and I mean several) Intent objects to deal with this.

        // First, the actual sharing Intent:
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType(fileType)
            .putExtra(Intent.EXTRA_STREAM, fileUri)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // To capture the result, we need an Intent that reaches our broadcast receiver:
        val resultIntent = Intent(context, FileSharerReceiver::class.java)
        val resultFlags = PendingIntent.FLAG_UPDATE_CURRENT

        // And to hand over the result Intent to the system, we need a PendingIntent wrapper:
        val pendingIntent = PendingIntent.getBroadcast(context, 0, resultIntent, resultFlags)

        // Return the actual Intent that connects everything with a Chooser dialog:
        return Intent.createChooser(shareIntent, "Muun", pendingIntent.intentSender)
    }
}