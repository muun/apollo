package io.muun.apollo.presentation.ui.activity.extension

import android.app.AlertDialog
import android.content.DialogInterface
import androidx.annotation.StringRes
import butterknife.BindColor
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.utils.getStyledString
import rx.functions.Action0
import javax.inject.Inject

@PerActivity
class AlertDialogExtension @Inject constructor() : ActivityExtension() {

    @JvmField
    @BindColor(R.color.text_secondary_color)
    var negativeButtonColor = 0

    @JvmField
    @BindColor(R.color.red)
    var positiveButtonColor = 0

    private var activeDialog: AlertDialog? = null

    /**
     * Show an AlertDialog.
     */
    fun showDialog(dialog: MuunDialog) {
        dismissDialog()

        // ON dialog dismiss, dispose android's dialog reference to avoid memory leaks
        dialog.addOnDismissAction { dismissedDialog ->
            // If activeDialog has changed, it means dismissedDialog is already dismissed and a new
            // dialog is being shown. We don't want to lose that ref (to properly dismiss it later).
            if (activeDialog == dismissedDialog) {
                activeDialog = null
            }
        }

        activeDialog = dialog.show(activity)
        activeDialog!!.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(negativeButtonColor)
        activeDialog!!.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(positiveButtonColor)
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    fun showErrorDialog(
        @StringRes resId: Int,
        followup: Action0? = null,
        onDismiss: Action0? = null,
    ) {
        showErrorDialog(activity.getStyledString(resId), followup, onDismiss)
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    fun showErrorDialog(
        msg: CharSequence,
        followupAction: Action0? = null,
        onDismiss: Action0? = null,
    ) {
        val builder = MuunDialog.Builder()
            .layout(R.layout.dialog_custom_layout)
            .message(msg)
            .onDismiss { onDismiss?.call() }

        if (followupAction != null) {
            builder.positiveButton(R.string.error_dialog_let_us_know, followupAction)
        }

        showDialog(builder.build())
    }

    fun dismissDialog() {
        if (activeDialog != null) {
            activeDialog!!.dismiss()
            activeDialog = null
        }
    }

    override fun onStop() {
        super.onStop()
        dismissDialog()
    }
}