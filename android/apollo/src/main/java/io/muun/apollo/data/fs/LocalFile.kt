package io.muun.apollo.data.fs

import android.net.Uri
import java.io.File

class LocalFile(
    val uri: Uri,
    val type: String,
    val path: String
) {

    fun toFile() =
        File(path)
}