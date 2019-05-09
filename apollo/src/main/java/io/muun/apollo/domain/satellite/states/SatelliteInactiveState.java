package io.muun.apollo.domain.satellite.states;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteInactiveState implements BaseSatelliteState {

    /**
     * Json constructor.
     */
    public SatelliteInactiveState() {
    }

    @Override
    public SatelliteScreen getScreen() {
        return SatelliteScreen.INACTIVE;
    }
}
