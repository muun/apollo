package io.muun.apollo.presentation.ui.view

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.google.android.material.imageview.ShapeableImageView
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.utils.getResourceIdentifier
import io.muun.apollo.presentation.ui.utils.isAndroidResourceUri
import io.muun.common.Optional
import java.io.File

class ProfilePictureView  @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0)
    : ShapeableImageView(c, a, s) {

    interface ImageLoadListener {
        fun onLoadFinished(uri: Uri?)
    }

    @JvmField
    @State
    var pictureUri: Uri? = null

    private var listener: ImageLoadListener? = null

    fun setListener(listener: ImageLoadListener) {
        this.listener = listener
    }

    /**
     * Set the profile picture url.
     */
    fun setPictureUri(pictureUri: String?) {
        setPictureUri(if (pictureUri != null) Uri.parse(pictureUri) else null)
    }

    /**
     * Set the profile picture url.
     */
    fun setPictureUri(pictureUri: Uri?) {
        loadPictureUri(pictureUri)
    }

    /**
     * Reset the profile picture to the default image.
     */
    fun setDefaultPictureUri() {
        loadPictureUri(null)
    }

    private fun loadPictureUri(pictureUri: Uri?) {
        if (!canRequestImageFor(context)) {
            return
        }

        // If we are effectively CHANGING the picture shown, then let's make this view visible
        // (otherwise Glide won't load the image) and reset background (a.k.a the previous image)
        if (pictureUri == null || pictureUri != this.pictureUri) {
            visibility = VISIBLE
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Signatures are Glide way to deal with changes in uri's content.
        // If the content of a uri changes and the uri stays unchanged, Glide's cache always
        // return same value, even though underlying content has changed)
        val signature: String = if (isLocalFileUri(pictureUri)) {
            val tempFile = File(pictureUri!!.path!!)
            tempFile.lastModified().toString()
        } else {
            "unchanged"
        }

        Glide.with(context)
            .load(context, pictureUri)
            .apply(RequestOptions.signatureOf(ObjectKey(signature)))
            .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.RESOURCE))
            .apply(RequestOptions.circleCropTransform())
            .error(Glide.with(context).load(R.drawable.avatar_badge_grey))
            .listener(object : RequestListener<Drawable?> {

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any,
                    target: Target<Drawable?>,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any,
                    target: Target<Drawable?>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (isNewPictureUri(pictureUri)) {
                        this@ProfilePictureView.pictureUri = pictureUri
                        onPictureChange(pictureUri)
                    }
                    return false
                }
            })
            .into(this)
    }

    fun getPictureUri(): Optional<Uri> {
        return Optional.ofNullable(pictureUri)
    }

    private fun isLocalFileUri(pictureUri: Uri?): Boolean {
        return pictureUri != null && "file" == pictureUri.scheme
    }

    private fun isNewPictureUri(uri: Uri?): Boolean {
        return uri != null && uri != pictureUri
    }

    private fun onPictureChange(uri: Uri?) {
        if (listener != null) {
            listener!!.onLoadFinished(uri)
        }
    }

    companion object {
        private fun canRequestImageFor(context: Context?): Boolean {
            if (context == null) {
                return false
            }

            if (context is Activity) {
                return !context.isDestroyed && !context.isFinishing
            }
            return true
        }
    }
}

fun RequestManager.load(context: Context, uri: Uri?): RequestBuilder<Drawable> =
    if (uri != null && uri.isAndroidResourceUri()) {
       load(ContextCompat.getDrawable(context, context.resources.getResourceIdentifier(uri)))
    } else {
        load(uri)
    }