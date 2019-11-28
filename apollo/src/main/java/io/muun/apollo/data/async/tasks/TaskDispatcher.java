package io.muun.apollo.data.async.tasks;

import io.muun.apollo.domain.action.AddressActions;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.IntegrityActions;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.errors.PeriodicTaskOnMainThreadError;

import android.os.Looper;
import rx.Observable;
import rx.functions.Func0;
import timber.log.Timber;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TaskDispatcher {

    private final Map<String, Func0<Observable<Void>>> nameToHandler = new HashMap<>();

    /**
     * Configure task handlers.
     */
    @Inject
    public TaskDispatcher(ContactActions contactActions,
                          AddressActions addressActions,
                          NotificationActions notificationActions,
                          IntegrityActions integrityActions,
                          FetchRealTimeDataAction fetchRealTimeData) {

        registerTaskType("pullNotifications", notificationActions::pullNotifications);

        registerTaskType("syncRealTimeData", fetchRealTimeData::action);
        registerTaskType("syncPhoneContacts", contactActions::syncPhoneContacts);
        registerTaskType("syncExternalAddressesIndexes",
                addressActions::syncExternalAddressesIndexes);

        registerTaskType("checkIntegrity", integrityActions::checkIntegrity);
    }

    /**
     * Dispatch a task to the corresponding handler.
     */
    public Observable<Void> dispatch(String taskName) {

        final Func0<Observable<Void>> handler = nameToHandler.get(taskName);

        if (handler == null) {
            return Observable.error(new Throwable("Unrecognized task type: " + taskName));
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Timber.e(new PeriodicTaskOnMainThreadError(taskName));
        }

        return handler.call();
    }

    /**
     * Associate a task type with its handler and payload type.
     */
    private void registerTaskType(String type, Func0<Observable<Void>> handler) {

        nameToHandler.put(type, handler);
    }
}
