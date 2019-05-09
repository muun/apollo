package io.muun.apollo.domain.satellite.messages;

import io.muun.common.api.messages.AbstractMessage;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetSatelliteStateMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "satellite/getState",
            SessionStatus.LOGGED_IN,
            MessageOrigin.SATELLITE
    );

    /**
     * Json and Apollo constructor.
     */
    public GetSatelliteStateMessage() {
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
