package io.muun.apollo.presentation.app.trace

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * ContentProvider which injects ttid tracer into application lifecycle.
 *
 * This was designed to be as non-disruptive (no code injection in ApolloApplication) as possible.
 * ContentProviders run before Application#onCreate so in this particular case it also gives better
 * accuracy when tracing app timing.
 */
class StartupTraceProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val applicationContext = requireNotNull(context?.applicationContext)
        val application = applicationContext as Application
        StartupTtidTrace.register(application)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?,
    ): Cursor? {
        throw IllegalStateException("Not allowed.")
    }

    override fun getType(uri: Uri): String? {
        throw IllegalStateException("Not allowed.")
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        throw IllegalStateException("Not allowed.")
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String?>?,
    ): Int {
        throw IllegalStateException("Not allowed.")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String?>?,
    ): Int {
        throw IllegalStateException("Not allowed.")
    }
}