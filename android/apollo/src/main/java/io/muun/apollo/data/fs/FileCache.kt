package io.muun.apollo.data.fs

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.muun.apollo.data.external.Globals
import java.io.File
import javax.inject.Inject

class FileCache @Inject constructor(val context: Context) {

    companion object {
        const val DIRECTORY = "cache"

        val AUTHORITY by lazy {
            // Using `lazy` to allow initialization of Globals
            "${Globals.INSTANCE.applicationId}.cache.fileprovider"
        }
    }

    enum class Entry(val fileName: String) {
        EMERGENCY_KIT_NO_META("tmp_ek_no_meta.pdf"),
        EMERGENCY_KIT("Muun-Emergency-Kit.pdf"),
        LOGCAT("logcat.txt")
    }

    fun get(entry: Entry): LocalFile =
        LocalFile(getUri(entry), getMimeType(entry), getFile(entry).absolutePath)

    fun getFile(entry: Entry): File =
        File(File(context.filesDir, DIRECTORY), entry.fileName)

    private fun getUri(entry: Entry): Uri =
        FileProvider.getUriForFile(context, AUTHORITY, getFile(entry))

    private fun getMimeType(entry: Entry): String =
        context.contentResolver.getType(getUri(entry)) ?: "application/octet-stream"

    fun delete(entry: Entry) =
        getFile(entry).delete()
}