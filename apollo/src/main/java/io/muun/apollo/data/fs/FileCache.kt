package io.muun.apollo.data.fs

import android.content.Context
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
        EMERGENCY_KIT("Muun-Emergency-Kit.pdf")
    }

    fun getFile(entry: Entry) =
        File(File(context.filesDir, DIRECTORY), entry.fileName)

    fun getUri(entry: Entry) =
        FileProvider.getUriForFile(context, AUTHORITY, getFile(entry))

    fun getMimeType(entry: Entry) =
        context.contentResolver.getType(getUri(entry)) ?: "application/octet-stream"
}