package io.muun.apollo.data.os;

import io.muun.apollo.BuildConfig;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.domain.errors.ConnectivityError;

import android.content.Context;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import rx.Observable;

import java.io.IOException;

import javax.inject.Inject;

public class GcmTokenProvider {

    private final InstanceID instanceId;
    private final String senderId;

    /**
     * Constructor.
     */
    @Inject
    public GcmTokenProvider(Context context, Configuration config) {
        final String gcmSenderKey = String.format("gcm.sender.%s", BuildConfig.FLAVOR);
        this.instanceId = InstanceID.getInstance(context);
        this.senderId = config.getString(gcmSenderKey);
    }

    /**
     * Fetch the Google Cloud Messaging registration token.
     */
    public Observable<String> getToken() {
        return Observable.defer(() -> {
            try {
                final String token =
                        instanceId.getToken(senderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE);
                return Observable.just(token);
            } catch (IOException e) {
                Logger.error(e);
                return Observable.error(new ConnectivityError(e));
            }
        });
    }
}
