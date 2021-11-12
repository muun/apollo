package io.muun.apollo.data.os.sharer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi


class FileSharer(val context: Context) {

    /**
     * The result of a choice in a share dialog.
     */
    class Selection(val className: String?)

    companion object {
        /**
         * Function to report results from the share dialog. This could be much more sophisticated
         * (supporting an event queue, multiple listeners, etc) but the UI can't do concurrent shares
         * so it's not necessary.
         */
        var onSelectionListener = { _: Selection -> }
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