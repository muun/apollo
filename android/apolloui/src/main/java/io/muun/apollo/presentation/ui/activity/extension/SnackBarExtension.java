package io.muun.apollo.presentation.ui.activity.extension;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.BugDetected;
import io.muun.apollo.presentation.ui.base.ActivityExtension;
import io.muun.apollo.presentation.ui.base.di.PerActivity;

import android.view.Gravity;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import timber.log.Timber;

import javax.inject.Inject;

@PerActivity
public class SnackBarExtension extends ActivityExtension {

    @Nullable
    private Snackbar snackbar;

    @Inject
    public SnackBarExtension() {
    }

    /**
     * Show a dismissible SnackBar of indefinite duration.
     */
    public void showSnackBarIndefinite(int messageResId) {
        showSnackBarIndefinite(getActivity().getString(messageResId));
    }

    public void showSnackBarIndefinite(CharSequence text) {
        showSnackBarIndefinite(text, true, null);
    }

    /**
     * Show a SnackBar of indefinite duration with a specific height.
     */
    public void showSnackBarIndefinite(int messageResId, boolean dismissible, Float height) {
        showSnackBarIndefinite(getActivity().getString(messageResId), dismissible, height);
    }

    /**
     * Show a SnackBar of indefinite duration with a specific height.
     */
    public void showSnackBarIndefinite(CharSequence text, boolean dismissible, Float height) {

        // TODO if a snackbar is showing (snackbar != null) should what should we do?
        // We are choosing to replace the previous one and log an error (it shouldn't happen)

        if (snackbar != null) {
            snackbar.dismiss();
            Timber.e(new BugDetected(
                    "Trying to show multiple snackbars in " + getActivity().getClass().getName()
            ));
        }

        snackbar = Snackbar.make(
                getActivity().findViewById(android.R.id.content),
                text,
                Snackbar.LENGTH_INDEFINITE
        );

        if (dismissible) {
            snackbar.setAction(R.string.dismiss, v -> snackbar.dismiss());
        }

        if (height != null) {
            final TextView snackbarText = snackbar
                    .getView()
                    .findViewById(com.google.android.material.R.id.snackbar_text);

            // We use the Snackbar to hide a button, so we round up the pixels just in case:
            snackbarText.getLayoutParams().height = (int) (height + 0.5f);
            snackbarText.setGravity(Gravity.CENTER_VERTICAL);
        }

        snackbar.show();
    }

    @Override
    public void onStop() {
        dismissSnackBar();
    }

    /**
     * Dismiss SnackBar.
     */
    public void dismissSnackBar() {
        if (snackbar != null) {
            snackbar.dismiss();
        }
    }
}
