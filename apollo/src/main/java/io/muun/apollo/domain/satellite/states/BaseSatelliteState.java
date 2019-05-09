package io.muun.apollo.domain.satellite.states;

import io.muun.apollo.domain.satellite.messages.SatelliteStateMessage;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface BaseSatelliteState {

    @JsonIgnore
    SatelliteScreen getScreen();

    @JsonIgnore
    default SatelliteStateMessage getStateMessage() {
        return new SatelliteStateMessage(getScreen(), this);
    }
}
