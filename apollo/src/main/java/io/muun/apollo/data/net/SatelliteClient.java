package io.muun.apollo.data.net;

import io.muun.apollo.data.net.base.BaseClient;
import io.muun.apollo.data.os.DeviceInfoProvider;
import io.muun.apollo.domain.model.SatellitePairing;
import io.muun.apollo.domain.satellite.messages.CompletePairingMessage;
import io.muun.common.api.beam.notification.NotificationPriorityJson;
import io.muun.common.api.beam.notification.NotificationRequestJson;
import io.muun.common.api.houston.HoustonService;
import io.muun.common.api.messages.Message;
import io.muun.common.rx.RxHelper;

import rx.Observable;

import javax.inject.Inject;


public class SatelliteClient extends BaseClient<HoustonService> {

    private final DeviceInfoProvider deviceInfoProvider;

    /**
     * Constructor.
     */
    @Inject
    public SatelliteClient(DeviceInfoProvider deviceInfoProvider) {
        // NOTE:
        // There is no actual Satellite API, other than using Houston to send messages. This
        // class provides a layer of abstraction, exposing relevant methods that end up mapping
        // to Houston endpoints.
        super(HoustonService.class);

        this.deviceInfoProvider = deviceInfoProvider;
    }

    /**
     * Begin our side of the pairing flow, coordinating with Houston and Satellite.
     */
    public Observable<SatellitePairing> beginPairing(String satelliteSessionUuid) {
        return getService().createReceivingSession(satelliteSessionUuid)
                .map(apolloSessionUuid -> new CompletePairingMessage(
                        apolloSessionUuid,
                        deviceInfoProvider.getDeviceName(),
                        deviceInfoProvider.getDeviceModel(),
                        deviceInfoProvider.getDeviceManufacturer(),
                        deviceInfoProvider.getApolloVersionName(),
                        deviceInfoProvider.getApolloVersionCode()
                ))
                .flatMap(message -> sendMessage(satelliteSessionUuid, message)
                        .map(ignored -> SatellitePairing.createWaitingForPeer(
                                satelliteSessionUuid,
                                message.apolloSessionUuid
                        ))
                );
    }

    /**
     * Expire a pairing, by closing both ends via Houston.
     */
    public Observable<Void> expirePairing(SatellitePairing pairing) {
        return Observable.zip(
                getService().expireReceivingSession(pairing.satelliteSessionUuid),
                getService().expireReceivingSession(pairing.apolloSessionUuid),
                RxHelper::toVoid
        );
    }

    /**
     * Send a message to a Satellite instance.
     */
    public Observable<Void> sendMessage(String sessionUuid, Message message) {

        final NotificationRequestJson notificationRequest = new NotificationRequestJson(
                NotificationPriorityJson.HIGH,
                message.getSpec().messageType,
                message,
                null
        );

        return getService().sendSatelliteNotification(sessionUuid, notificationRequest)
                .map(RxHelper::toVoid);
    }

}