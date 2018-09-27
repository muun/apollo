package io.muun.apollo.data.gcm;

import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.di.DataComponentProvider;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.GcmTokenProvider;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.common.rx.RxHelper;

import com.google.android.gms.iid.InstanceIDListenerService;

import javax.inject.Inject;

public class GcmTokenListenerService extends InstanceIDListenerService {

    @Inject
    DaoManager daoManager;

    @Inject
    HoustonClient houstonClient;

    @Inject
    GcmTokenProvider gcmTokenProvider;

    @Inject
    ExecutionTransformerFactory executionTransformerFactory;

    @Override
    public void onCreate() {
        super.onCreate();
        final DataComponentProvider provider = (DataComponentProvider) getApplication();
        provider.getDataComponent().inject(this);
        Logger.info("Starting GcmTokenListenerService");
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of the previous token
     * had been compromised. This call is initiated by the InstanceID provider.
     */
    @Override
    public void onTokenRefresh() {

        Logger.info("Received a new GCM token");

        gcmTokenProvider.getToken()
                .flatMap(houstonClient::updateGcmToken)
                .compose(executionTransformerFactory.getAsyncExecutor())
                .subscribe(
                        RxHelper::nop,
                        error -> Logger.error(error, "Error while trying to refresh GCM token")
            );
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.info("Destroying GcmTokenListenerService");
    }
}
