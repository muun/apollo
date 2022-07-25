package io.muun.apollo.presentation.app

import android.content.Context
import android.content.Intent
import io.muun.apollo.data.fs.FileCache
import timber.log.Timber
import javax.inject.Inject

class Logcat @Inject constructor(private val context: Context, private val fileCache: FileCache) {

    fun addLogsAsAttachment(emailIntent: Intent) {

        // Load/Write our app's logs to a local file
        val outputFile = fileCache.getFile(FileCache.Entry.LOGCAT)
        try {
            Runtime.getRuntime().exec("logcat -f " + outputFile.absolutePath)
        } catch (e: Throwable) {
            Timber.e(RuntimeException("Error accessing app logs", e))
            return
        }

        // Grant Uri permissions to email app's (e.g required by Gmail to successfully attach file)
        val resInfoList = context.packageManager.queryIntentActivities(emailIntent, 0)
        for (resolveInfo in resInfoList) {
            context.grantUriPermission(
                resolveInfo.activityInfo.packageName,
                fileCache.get(FileCache.Entry.LOGCAT).uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        // Add file as attachment to email intent
        emailIntent.putExtra(Intent.EXTRA_STREAM, fileCache.get(FileCache.Entry.LOGCAT).uri)
    }
}