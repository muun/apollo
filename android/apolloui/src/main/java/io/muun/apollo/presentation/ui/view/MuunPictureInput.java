package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.domain.action.images.DecodeImageAction;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.errors.p2p.InvalidPictureError;
import io.muun.common.Optional;
import io.muun.common.exception.MissingCaseError;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import butterknife.BindString;
import butterknife.BindView;
import icepick.State;
import timber.log.Timber;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.inject.Inject;


public class MuunPictureInput extends MuunView {

    private static final int REQUEST_DRAWER = 1;
    private static final int REQUEST_PICTURE = 2;

    private static final int CAMERA_ACTION  = 1;
    private static final int GALLERY_ACTION = 2;

    public interface OnChangeListener {
        /**
         * This method will be called on picture loading success, with the picture's local Uri.
         */
        void onPictureChange(Uri pictureUri);
    }

    public interface OnErrorListener {
        /**
         * This method will be called on picture loading failure, with the specific error
         * encountered.
         */
        void onPictureError(UserFacingError error);
    }

    @BindView(R.id.muun_picture_input_button)
    protected ProfilePictureView profilePictureView;

    @BindView(R.id.muun_button_progress_bar)
    ProgressBar progressBar;

    @BindString(R.string.action_camera)
    String cameraActionLabel;

    @BindString(R.string.action_gallery)
    String galleryActionLabel;

    // -----------------------------

    @Inject
    DecodeImageAction decodeImage;

    private OnChangeListener changeListener;
    private OnErrorListener errorListener;

    @State
    boolean loading;

    public MuunPictureInput(Context context) {
        super(context);
    }

    public MuunPictureInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunPictureInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_picture_input;
    }

    @Override
    protected void setUp(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        if (getComponent() != null) {
            getComponent().inject(this);
        }

        profilePictureView.setOnClickListener(view -> openIntentChooser());
        profilePictureView.setListener((Uri uri) -> toggleLoading(false));
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Parcelable parcelable) {
        super.onRestoreInstanceState(parcelable);
        toggleLoading(loading);
    }

    /**
     * Enable/Disable loading progress bar, along with onClickListener
     * (to avoid handling click while loading).
     */
    public void toggleLoading(boolean loading) {
        this.loading = loading;
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        profilePictureView.setOnClickListener(loading ? null : view -> openIntentChooser());

        if (loading) {
            profilePictureView.setVisibility(View.GONE);

        } else {
            profilePictureView.setVisibility(View.VISIBLE);
        }
    }

    public Optional<Uri> getPictureUri() {
        return profilePictureView.getPictureUri();
    }

    /**
     * Set a Bitmap as value, loading an Uri.
     */
    public void setPicture(String uriString) {
        if (uriString != null && !uriString.isEmpty()) {
            setPicture(Uri.parse(uriString));
        }
    }

    /**
     * Set a Bitmap as value, loading an Uri.
     */
    public void setPicture(@Nullable Uri pictureUri) {
        if (pictureUri != null) {
            profilePictureView.setPictureUri(pictureUri);
        }
    }

    /**
     * Reset the profile picture to the default image.
     */
    public void clearPicture() {
        profilePictureView.setDefaultPictureUri();
    }

    /**
     * Reload the cached pictureUri, if any.
     */
    public void resetPicture() {
        final Optional<Uri> optionalUri = getPictureUri();
        setPicture(optionalUri.isPresent() ? optionalUri.get() : null);
    }

    public void setOnChangeListener(OnChangeListener listener) {
        this.changeListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.errorListener = listener;
    }

    private void openIntentChooser() {
        final DialogFragment drawerFragment = new DrawerDialogFragment()
                .setTitle(R.string.action_pick_profile_pic_title)
                .addAction(CAMERA_ACTION, R.drawable.ic_camera_24_px, cameraActionLabel)
                .addAction(GALLERY_ACTION, R.drawable.ic_gallery_24_px, galleryActionLabel);

        requestExternalResult(REQUEST_DRAWER, drawerFragment);
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_DRAWER:
                onDrawerResult(resultCode);
                break;

            case REQUEST_PICTURE:
                onPictureResult(resultCode, data);
                break;

            default:
                throw new MissingCaseError(requestCode, "MuunPictureInput requestCode");
        }
    }

    @Override
    public void onPermissionsGranted(@NonNull String[] grantedPermissions) {
        super.onPermissionsGranted(grantedPermissions);
        launchCameraIntent();
    }

    private void handleCameraIntent() {
        if (!allPermissionsGranted(Manifest.permission.CAMERA)) {
            requestPermissions(Manifest.permission.CAMERA);

        } else {
            launchCameraIntent();
        }
    }

    private void launchCameraIntent() {
        final Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        toggleLoading(true);

        requestExternalResult(REQUEST_PICTURE, cameraIntent);
    }

    private void handleGalleryIntent() {
        final Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        toggleLoading(true);

        requestExternalResult(REQUEST_PICTURE, galleryIntent);
    }

    private void onDrawerResult(int resultCode) {
        switch (resultCode) {
            case CAMERA_ACTION:
                handleCameraIntent();
                break;

            case GALLERY_ACTION:
                handleGalleryIntent();
                break;

            default:
                throw new MissingCaseError(resultCode, "MuunPictureInput drawer actionId");
        }
    }

    private void onPictureResult(int resultCode, Intent result) {
        if (resultCode == Activity.RESULT_CANCELED) {
            toggleLoading(false);
            return;
        }

        try {
            final Uri pictureUri = readResultIntent(result);

            if (changeListener != null) {
                changeListener.onPictureChange(pictureUri);
            }

        } catch (InvalidPictureError error) {
            if (errorListener != null) {
                errorListener.onPictureError(error);
            }
        }
    }

    private Uri readResultIntent(Intent result) throws InvalidPictureError {

        if (result == null) {
            throw new InvalidPictureError();
        }

        final Bitmap bitmap;

        if (result.getData() != null) {
            // When picture comes from Gallery or 3rd party app, the URI comes in intent's data
            bitmap = decodeImage.run(result.getData());

        } else {

            // When picture comes from Camera intent, result is a bitmap in intent's extras bundle
            if (result.getExtras() == null || result.getExtras().get("data") == null) {
                throw new InvalidPictureError();
            }

            bitmap = (Bitmap) result.getExtras().get("data");
        }

        if (bitmap == null) {
            // This is VERY weird, but has happened so lets gather as much data as we can
            final InvalidPictureError error = new InvalidPictureError(result);
            Timber.e(error);
            throw error;
        }

        return storeInLocalTempFile(bitmap);
    }

    private Uri storeInLocalTempFile(Bitmap bitmap) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        final byte[] bitmapData = bytes.toByteArray();

        final File tempFile = new File(getContext().getFilesDir(), "tempProfilePic.jpg");

        try {
            final FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(bitmapData);
            fos.flush();
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return Uri.fromFile(tempFile);
    }
}
