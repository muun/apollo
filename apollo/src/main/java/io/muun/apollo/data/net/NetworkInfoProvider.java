package io.muun.apollo.data.net;


import io.muun.common.Optional;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import rx.Observable;
import rx.subjects.BehaviorSubject;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NetworkInfoProvider {

    private static final String CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";

    private final Context context;
    private final ConnectivityManager connectivityManager;

    private final BehaviorSubject<Optional<NetworkInfo>> subject;
    private ConnectivityChangeReceiver receiver;

    /**
     * Constructor.
     */
    @Inject
    public NetworkInfoProvider(Context context) {
        this.context = context;

        connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        subject = BehaviorSubject.create(getCurrentNetworkInfo());
    }

    public Observable<Optional<NetworkInfo>> watchNetworkInfo() {
        return subject.asObservable();
    }

    /**
     * Register a system BroadcastReceiver for connectivity info. Start emitting through
     * `watchNetworkInfo`.
     */
    public void startReceiving() {
        if (isReceiving()) {
            // Here, we force a restart. This gives callers a fresh BroadcastReceiver, in case the
            // active one died. This seems to happen on some devices.
            stopReceiving();
        }

        receiver = new ConnectivityChangeReceiver();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(CONNECTIVITY_CHANGE);

        Timber.d("NETWORK INFO: adding receiver");
        context.registerReceiver(receiver, filter);
    }

    /**
     * Unregister the system BroadcastReceiver. Stop emmiting through `watchNetworkInfo`. Note that
     * the Observable *does not complete* after this call.
     */
    public void stopReceiving() {
        if (! isReceiving()) {
            return;
        }

        context.unregisterReceiver(receiver);
        receiver = null;
    }

    public boolean isReceiving() {
        return receiver != null;
    }

    public Optional<NetworkInfo> getCurrentNetworkInfo() {
        return Optional.ofNullable(connectivityManager.getActiveNetworkInfo());
    }

    private class ConnectivityChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            subject.onNext(getCurrentNetworkInfo());
        }
    }
}
