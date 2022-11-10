package io.muun.apollo.data.os

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import io.muun.apollo.domain.errors.p2p.InvalidPictureError
import java.io.IOException
import javax.inject.Inject

class ImageDecoder @Inject constructor(private val context: Context) {

    /**
     * Decode an image Bitmap from the Uri of the image file.
     * Note: Apparently old Image API sometimes returned a null bitmap so we return a Bitmap? type.
     */
    @Suppress("LiftReturnOrAssignment")
    fun decode(data: Uri): Bitmap? {

        val contentResolver: ContentResolver = context.contentResolver

        try {
            if (OS.supportsImageDecoderApi()) {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, data))
            } else {
                return MediaStore.Images.Media.getBitmap(contentResolver, data)
            }

        } catch (e: IOException) {
            throw InvalidPictureError()
        }
    }
}