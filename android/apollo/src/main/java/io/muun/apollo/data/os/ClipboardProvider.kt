package io.muun.apollo.data.os

import android.content.ClipData
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Context
import rx.Observable
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * This component CAN'T be injected into any component that can be initialized in background
 * (e.g MuunWorkerFactory, NotificationProcessor, etc...), as it constructor depends on a system
 * call that can only be made from the Main thread.
 */
class ClipboardProvider @Inject constructor(context: Context) {

    private val clipboard: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /**
     * Copy some text to the clipboard.
     *
     * @param label a nullable NON user-visible label used mainly to identify the clip data. It is
     * usually null when Android components use clipboard. Disregard javadoc from
     * ClipData.newPlainText(). See
     * [this SO answer.](https://stackoverflow.com/a/39504849/901465).
     * @param text  The actual text to copy, or null to clear clipboard.
     */
    fun copy(label: String?, text: String?) {
        val clip = ClipData.newPlainText(label, text)
        try {
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            // Apparently sometimes, if another app is listening to the clipboard a
            // SecurityException could be thrown (if the right permission, config is set).
            // Source: https://stackoverflow.com/a/65441899/901465
            // TODO: should we warn the user that this may be happening?
            Timber.e("This shouldn't really happen... but here we are", e)
        }
    }

    /**
     * Create an Observable that reports changes on the system clipboard (fires on subscribe).
     */
    fun watchPrimaryClip(): Observable<String> {
        return Observable
            .interval(250, TimeUnit.MILLISECONDS) // emits sequential numbers 0+ on each tick
            .startWith(-1L) // emits -1 immediately (since interval waits for the first delay)
            .map { paste() ?: "" }
            .distinctUntilChanged()
    }

    /**
     * Grab some text from the clipboard.
     * NOTE: starting from Android 12 (api 31) this triggers a Clipboard Access visual notification
     * on screen.
     */
    fun paste(): String? {
        if (!clipboard.hasPrimaryClip()) {
            return null
        }
        val primaryClip = clipboard.primaryClip
        if (primaryClip == null || primaryClip.itemCount == 0) {
            return null
        }
        val item = primaryClip.getItemAt(0)
        return item?.text?.toString()
    }

    /**
     * Create an Observable that reports changes on the system clipboard (fires on subscribe).
     */
    fun watchForPlainText(): Observable<Boolean> {
        return Observable
            .interval(250, TimeUnit.MILLISECONDS) // emits sequential numbers 0+ on each tick
            .startWith(-1L) // emits -1 immediately (since interval waits for the first delay)
            .map { hasPlainText() }
            .distinctUntilChanged()
    }

    private fun hasPlainText(): Boolean {
        return clipboard.hasPrimaryClip()
            && clipboard.primaryClipDescription!!.hasMimeType(MIMETYPE_TEXT_PLAIN)
    }
}