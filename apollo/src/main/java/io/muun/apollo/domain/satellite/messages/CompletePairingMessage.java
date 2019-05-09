package io.muun.apollo.domain.satellite.messages;

import io.muun.common.api.messages.AbstractMessage;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletePairingMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "satellite/completePairing",
            SessionStatus.LOGGED_IN,
            MessageOrigin.APOLLO
    );

    public String apolloSessionUuid;

    public String deviceName;
    public String deviceModel;
    public String deviceManufacturer;

    public String versionName;
    public int versionCode;

    /**
     * Json constructor.
     */
    public CompletePairingMessage() {
    }

    /**
     * Apollo constructor.
     */
    public CompletePairingMessage(String apolloSessionUuid,
                                  String deviceName,
                                  String deviceModel,
                                  String deviceManufacturer,
                                  String versionName, int versionCode) {
        this.apolloSessionUuid = apolloSessionUuid;
        this.deviceName = deviceName;
        this.deviceModel = deviceModel;
        this.deviceManufacturer = deviceManufacturer;
        this.versionName = versionName;
        this.versionCode = versionCode;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
