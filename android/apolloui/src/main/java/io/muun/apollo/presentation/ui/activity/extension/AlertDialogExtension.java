package io.muun.apollo.presentation.ui.activity.extension;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.ActivityExtension;
import io.muun.apollo.presentation.ui.base.di.PerActivity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import butterknife.BindColor;
import rx.functions.Action0;

import javax.inject.Inject;

@PerActivity
public class AlertDialogExtension extends ActivityExtension {

    @BindColor(R.color.muun_gray_dark)
    int negativeButtonColor;

    @BindColor(R.color.muun_red)
    int positiveButtonColor;

    private AlertDialog activeDialog;

    @Inject
    public AlertDialogExtension() {
    }

    /**
     * Show an AlertDialog.
     */
    public void showDialog(MuunDialog dialog) {
        dismissDialog();

        // ON dialog dismiss, dispose android's dialog reference to avoid memory leaks
        dialog.addOnDismissAction(alertDialog -> activeDialog = null);

        activeDialog = dialog.show(getActivity());

        activeDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(negativeButtonColor);
        activeDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(positiveButtonColor);
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    public void showErrorDialog(String errorMsg, Action0 followupAction, Action0 onDismissAction) {
        final MuunDialog errorDialog = new MuunDialog.Builder()
                .layout(R.layout.dialog_custom_layout)
                .message(errorMsg)
                .positiveButton(R.string.error_dialog_let_us_know, followupAction)
                .onDismiss(dialog -> {
                    if (onDismissAction != null) {
                        onDismissAction.call();
                    }
                })
                .build();

        showDialog(errorDialog);
    }

    public void dismissDialog() {
        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        dismissDialog();
    }
}
