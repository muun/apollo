package io.muun.apollo.data.os;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import rx.Subscriber;

public class SubscriberContentObserver extends ContentObserver {

    private Subscriber<? super String> subscriber;

    SubscriberContentObserver() {
        super(new Handler(Looper.getMainLooper()));
    }

    public void setSubscriber(Subscriber<? super String> subscriber) {
        this.subscriber = subscriber;
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
