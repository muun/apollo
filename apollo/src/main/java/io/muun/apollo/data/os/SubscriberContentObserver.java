package io.muun.apollo.data.os;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import rx.Subscriber;

public class SubscriberContentObserver extends ContentObserver {

    Subscriber<? super String> subscriber;

    public SubscriberContentObserver() {
        super(new Handler(Looper.getMainLooper()));
    }

    /**
     * Set this ContentObserver's Subscriber. It will be immediately notified with a `null` Uri
     * if `notifyOnStart` was set to `true` on construction.
     */
    public SubscriberContentObserver setSubscriber(Subscriber<? super String> subscriber) {
        this.subscriber = subscriber;
        return this;
    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        notify(uri != null ? uri.toString() : "content://");
    }

    private void notify(String uriString) {
        if (subscriber != null && !subscriber.isUnsubscribed()) {
            subscriber.onNext(uriString); // this will always be invoked in the main thread.
        }
    }
}
