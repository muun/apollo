package io.muun.apollo.data.os;

import io.muun.common.Optional;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import rx.Observable;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * This component CAN'T be injected into any component that can be initialized in background
 * (e.g MuunWorkerFactory, NotificationProcessor, etc...), as it constructor depends on a system
 * call that can only be made from the Main thread.
 */
public class ClipboardProvider {

    private final ClipboardManager clipboard;

    @Inject
    public ClipboardProvider(Context context) {
        clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * Copy some text to the clipboard.
     *
     * @param label a nullable NON user-visible label used mainly to identify the clip data. It is
     *             usually null when Android components use clipboard. Disregard javadoc from
     *             ClipData.newPlainText(). See https://stackoverflow.com/a/39504849/901465.
     * @param text The actual text to copy, or null to clear clipboard.
     */
    public void copy(String label, String text) {

        final ClipData clip = ClipData.newPlainText(label, text);

        clipboard.setPrimaryClip(clip);
    }

    /**
     * Grab some text from the clipboard.
     */
    public Optional<String> paste() {

        if (!clipboard.hasPrimaryClip()) {
            return Optional.empty();
        }

        final ClipData primaryClip = clipboard.getPrimaryClip();
        if (primaryClip == null || primaryClip.getItemCount() == 0) {
            return Optional.empty();
        }

        final ClipData.Item item = primaryClip.getItemAt(0);
        if (item == null) {
            return Optional.empty();
        }

        final CharSequence text = item.getText();
        if (text == null) {
            return Optional.empty();
        }

        return Optional.of(text.toString());
    }

    /**
     * Create an Observable that reports changes on the system clipboard (fires on subscribe).
     */
    public Observable<String> watchPrimaryClip() {
        return Observable
                .interval(250, TimeUnit.MILLISECONDS) // emits sequential numbers 0+ on each tick
                .startWith(-1L) // emits -1 immediately (since interval waits for the first delay)
                .map(i -> paste().orElse(""))
                .distinctUntilChanged();
    }
}
