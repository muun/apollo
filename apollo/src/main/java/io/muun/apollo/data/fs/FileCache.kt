package io.muun.apollo.data.fs

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject

class FileCache @Inject constructor(val context: Context) {

    companion object {
        const val DIRECTORY = "cache"
        const val AUTHORITY = "io.muun.apollo.cache.fileprovider"
    }

    enum class Entry(val fileName: String) {
        EMERGENCY_KIT("ek.pdf")
    }

    fun getFile(entry: Entry) =
        File(File(context.filesDir, DIRECTORY), entry.fileName)

    fun getUri(entry: Entry) =
        FileProvider.getUriForFile(context, AUTHORITY, getFile(entry))

    fun getMimeType(entry: Entry) =
        context.contentResolver.getType(getUri(entry))
}