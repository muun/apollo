package io.muun.apollo.data.os.sharer

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
class FileSharerReceiver : BroadcastReceiver() {

    init {
        Timber.d("Instance created")
    }

    override fun onReceive(context: Context, intent: Intent) {
        // This method will be called when the user makes a selection in the share dialog.

        // Note that we won't receive an event if the dialog is dismissed by the user. If we're
        // here, it's because an application was selected. To handle dismissal, we need to
        // launch the Intent with `startActivityForResult` and handle RESULT_CANCELED. In other
        // words, we're not expecting to receive RESULT_CANCELED here, it shouldn't reach us.

        // Just as important, the event we receive is emitted by the share dialog itself, not
        // by the target application. We will receive RESULT_OK from the dialog even if the
        // target application crashes or closes immediately, and we can't really know what happened.

        Timber.d("Result code $resultCode received from dialog")

        // Attempt to obtain the component that handled the share Intent:
        val component = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
        val selection = FileSharer.Selection(component?.className)

        when (resultCode) {
            Activity.RESULT_OK -> FileSharer.onSelectionListener(selection)

            else ->
                Timber.e("Unexpected result $resultCode in ${this::class.java.simpleName}")
        }
    }
}