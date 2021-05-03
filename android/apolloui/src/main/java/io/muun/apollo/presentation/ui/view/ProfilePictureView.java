package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.common.Optional;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.imageview.ShapeableImageView;
import icepick.State;

import java.io.File;
import javax.annotation.Nullable;

public class ProfilePictureView extends ShapeableImageView {

    interface ImageLoadListener {
        void onLoadFinished(Uri uri);
    }

    @State
    Uri pictureUri = null;

    private ImageLoadListener listener;

    public ProfilePictureView(Context context) {
        super(context);
    }

    public ProfilePictureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ProfilePictureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setListener(ImageLoadListener listener) {
        this.listener = listener;
    }

    /**
     * Set the profile picture url.
     */
    public void setPictureUri(@Nullable String pictureUri) {
        setPictureUri(pictureUri != null ? Uri.parse(pictureUri) : null);
    }

    /**
     * Set the profile picture url.
     */
    public void setPictureUri(@Nullable Uri pictureUri) {
        loadPictureUri(pictureUri);
    }

    /**
     * Reset the profile picture to the default image.
     */
    public void setDefaultPictureUri() {
        loadPictureUri(null);
    }

    private void loadPictureUri(@Nullable Uri pictureUri) {
        final Context context = getContext();

        if (!canRequestImageFor(context)) {
            return;
        }

        // If we are effectively CHANGING the picture shown, then let's make this view visible
        // (otherwise Glide won't load the image) and reset background (a.k.a the previous image)
        if (pictureUri == null || !pictureUri.equals(this.pictureUri)) {
            setVisibility(View.VISIBLE);
            setBackgroundColor(Color.TRANSPARENT);
        }

        // Signatures are Glide way to deal with changes in uri's content.
        // If the content of a uri changes and the uri stays unchanged, Glide's cache always
        // return same value, even though underlying content has changed)
        final String signature;
        if (isLocalFileUri(pictureUri)) {
            final File tempFile = new File(pictureUri.getPath());
            signature = String.valueOf(tempFile.lastModified());
        } else {
            signature = "unchanged";
        }

        Glide.with(context)
                .load(pictureUri)
                .apply(RequestOptions.signatureOf(new ObjectKey(signature)))
                .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.RESOURCE))
                .apply(RequestOptions.circleCropTransform())
                .error(Glide.with(context).load(R.drawable.avatar_badge_grey))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            @androidx.annotation.Nullable final GlideException e,
                            final Object model,
                            final Target<Drawable> target,
                            final boolean isFirstResource
                    ) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            final Drawable resource,
                            final Object model,
                            final Target<Drawable> target,
                            final DataSource dataSource,
                            final boolean isFirstResource
                    ) {
                        if (isNewPictureUri(pictureUri)) {
                            ProfilePictureView.this.pictureUri = pictureUri;
                            onPictureChange(pictureUri);
                        }

                        return false;
                    }
                })
                .into(this);
    }

    public Optional<Uri> getPictureUri() {
        return Optional.ofNullable(pictureUri);
    }

    private boolean isLocalFileUri(Uri pictureUri) {
        return pictureUri != null && "file".equals(pictureUri.getScheme());
    }

    private boolean isNewPictureUri(Uri uri) {
        return uri != null && !uri.equals(ProfilePictureView.this.pictureUri);
    }

    private void onPictureChange(Uri uri) {
        if (listener != null) {
            listener.onLoadFinished(uri);
        }
    }

    private static boolean canRequestImageFor(final Context context) {
        if (context == null) {
            return false;
        }

        if (context instanceof Activity) {
            final Activity activity = (Activity) context;
            return !activity.isDestroyed() && !activity.isFinishing();
        }

        return true;
    }
}
