package io.muun.apollo.data.fs

import android.content.Context
import java.io.File
import javax.inject.Inject

class LibwalletDataDirectory @Inject constructor (context: Context) {

    val path: File =
        context.filesDir.resolve("libwallet")

    val socket: File =
        context.filesDir.resolve("wallet.sock")

    fun ensureExists() {
        path.mkdirs()
    }

    fun reset() {
        path.deleteRecursively()
        ensureExists()
    }
}