package io.muun.apollo.data.gcm;

import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.di.DataComponentProvider;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.ModelObjectsMapper;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.model.NotificationReport;
import io.muun.common.api.NotificationReportJson;

import android.os.Bundle;
import com.google.android.gms.gcm.GcmListenerService;

import javax.inject.Inject;

public class GcmMessageListenerService extends GcmListenerService {

    @Inject
    DaoManager daoManager; // not used directly by us, but needed in case we start the Application

    @Inject
    ModelObjectsMapper mapper;

    @Inject
    NotificationActions notificationActions;

    @Override
    public void onCreate() {
        super.onCreate();
        final DataComponentProvider provider = (DataComponentProvider) getApplication();
        provider.getDataComponent().inject(this);
        Logger.info("Starting GcmMessageListenerService");
    }

    /**
     * Called when message is received.
     *
     * @param from SenderID of the sender.
     * @param data Data bundle containing message data as key/value pairs. For Set of keys use
     *             data.keySet().
     */
    @Override
    public void onMessageReceived(String from, Bundle data) {
        final String message = data.getString("message");

        if (message == null) {
            return;
        }

        try {
            processMessage(message);

        } catch (Throwable error) {
            Logger.error(error, "While processing GCM message");
        }
    }

    private void processMessage(String message) {
        final NotificationReport report;

        try {
            report = mapper.mapNotificationReport(
                    SerializationUtils.deserializeJson(NotificationReportJson.class, message)
            );

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid NotificationReportJson: " + message, e);
        }

        notificationActions.onNotificationReport(report);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.info("Destroying GcmMessageListenerService");
    }
}
