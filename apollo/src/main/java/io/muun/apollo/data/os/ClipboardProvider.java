package io.muun.apollo.data.os;

import io.muun.common.Optional;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import rx.Observable;
import rx.Subscriber;

import javax.inject.Inject;

public class ClipboardProvider {

    private final ClipboardManager clipboard;

    @Inject
    public ClipboardProvider(Context context) {
        clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * Copy some text to the clipboard.
     *
     * @param label User-visible label for the pasted data.
     * @param text The actual text to copy.
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
        if (primaryClip.getItemCount() == 0) {
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
     * Create an Observable that reports changes on the system clipboard (fires immediately upon
     * subscribe).
     */
    public Observable<String> watchPrimaryClip() {
        return Observable.using(
                ClipboardListener::new,

                clipboardListener -> Observable.create((Observable.OnSubscribe<String>)
                        (subscriber) -> {
                            clipboardListener.setSubscriber(subscriber);
                            clipboardListener.onPrimaryClipChanged();

                            clipboard.addPrimaryClipChangedListener(clipboardListener);
                        }
                ),

                clipboard::removePrimaryClipChangedListener

        ).distinctUntilChanged();
    }

    private class ClipboardListener implements ClipboardManager.OnPrimaryClipChangedListener {
        Subscriber<? super String> subscriber;

        public void setSubscriber(Subscriber<? super String> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onPrimaryClipChanged() {
            if (subscriber != null && !subscriber.isUnsubscribed()) {
                subscriber.onNext(paste().orElse(""));
            }
        }
    }
}
