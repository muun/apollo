package io.muun.apollo.domain.satellite.messages;

import io.muun.apollo.domain.satellite.states.BaseSatelliteState;
import io.muun.apollo.domain.satellite.states.SatelliteScreen;
import io.muun.common.api.messages.AbstractMessage;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteStateMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "satellite/state",
            SessionStatus.LOGGED_IN,
            MessageOrigin.APOLLO
    );

    @NotNull
    public SatelliteScreen screen;

    @NotNull
    public Object state;

    /**
     * Json constructor.
     */
    public SatelliteStateMessage() {
    }

    /**
     * Apollo constructor.
     */
    public SatelliteStateMessage(SatelliteScreen screen, BaseSatelliteState state) {
        this.screen = screen;
        this.state = state;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
