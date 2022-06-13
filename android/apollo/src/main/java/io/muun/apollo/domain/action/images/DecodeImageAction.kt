package io.muun.apollo.domain.action.images

import android.graphics.Bitmap
import android.net.Uri
import io.muun.apollo.data.os.ImageDecoder
import javax.inject.Inject

class DecodeImageAction @Inject constructor(private val imageDecoder: ImageDecoder) {

    fun run(uri: Uri): Bitmap? =
        imageDecoder.decode(uri)
}