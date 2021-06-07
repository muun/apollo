package io.muun.apollo.presentation.ui.utils

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.AnyRes
import io.muun.common.utils.Preconditions

object Uri {

    /**
     * Get the ContentResolver URI of a resource.
     */
    @JvmStatic
    fun getResourceUri(context: Context, @AnyRes resourceId: Int): Uri {
        val uriString = String.format(
            "%s://%s/%s/%s",
            ContentResolver.SCHEME_ANDROID_RESOURCE,
            context.resources.getResourcePackageName(resourceId),
            context.resources.getResourceTypeName(resourceId),
            context.resources.getResourceEntryName(resourceId)
        )
        return Uri.parse(uriString)
    }
}

fun Uri.isAndroidResourceUri() =
    scheme == ContentResolver.SCHEME_ANDROID_RESOURCE

/**
 * Get resource id from a valid Android ResourceUri, as defined by Uri.isAndroidResourceUri().
 * E.g android.resource://my.package.name/drawable/my_drawable
 */
fun Resources.getResourceIdentifier(uri: Uri): Int {
    Preconditions.checkArgument(uri.isAndroidResourceUri())
    checkNotNull(uri.path)

    val parts = uri.schemeSpecificPart.removePrefix("//").split("/")
    return getIdentifier(parts[2], parts[1], parts[0])
}