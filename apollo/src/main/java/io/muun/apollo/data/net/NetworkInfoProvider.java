package io.muun.apollo.data.net;


import io.muun.apollo.data.logging.Logger;
import io.muun.common.Optional;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import rx.Observable;
import rx.subjects.BehaviorSubject;

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

        registerReceiver();
    }

    public Observable<Optional<NetworkInfo>> watchNetworkInfo() {
        return subject.asObservable();
    }

    private void registerReceiver() {
        receiver = new ConnectivityChangeReceiver();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(CONNECTIVITY_CHANGE);

        Logger.debug("NETWORK INFO: adding receiver");
        context.registerReceiver(receiver, filter);
    }

    private Optional<NetworkInfo> getCurrentNetworkInfo() {
        return Optional.ofNullable(connectivityManager.getActiveNetworkInfo());
    }

    private class ConnectivityChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            subject.onNext(getCurrentNetworkInfo());
        }
    }
}
