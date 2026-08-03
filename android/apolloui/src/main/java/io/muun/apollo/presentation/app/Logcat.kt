package io.muun.apollo.presentation.app

import android.net.Uri
import io.muun.apollo.data.fs.FileCache
import timber.log.Timber
import javax.inject.Inject

class Logcat @Inject constructor(private val fileCache: FileCache) {

    /**
     * Write logcat to cache file and return its content URI.
     */
    fun getLogsUri(): Uri? {
        val outputFile = fileCache.getFile(FileCache.Entry.LOGCAT)
        try {
            Runtime.getRuntime().exec("logcat -f " + outputFile.absolutePath)
        } catch (e: Throwable) {
            Timber.e(RuntimeException("Error accessing app logs", e))
            return null
        }
        return fileCache.get(FileCache.Entry.LOGCAT).uri
    }
}
