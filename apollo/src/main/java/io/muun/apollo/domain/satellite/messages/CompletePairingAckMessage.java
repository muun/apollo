package io.muun.apollo.domain.satellite.messages;

import io.muun.common.api.messages.AbstractMessage;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletePairingAckMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "satellite/completePairingAck",
            SessionStatus.LOGGED_IN,
            MessageOrigin.SATELLITE
    );

    public String browser;
    public String osVersion;
    public String ip;

    /**
     * Json constructor.
     */
    public CompletePairingAckMessage() {
    }

    /**
     * Apollo constructor.
     */
    public CompletePairingAckMessage(String browser, String osVersion, String ip) {
        this.browser = browser;
        this.osVersion = osVersion;
        this.ip = ip;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
