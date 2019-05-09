package io.muun.apollo.domain.satellite.states;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteEmptyState implements BaseSatelliteState {

    /**
     * Json constructor.
     */
    public SatelliteEmptyState() {
    }

    @Override
    public SatelliteScreen getScreen() {
        return SatelliteScreen.EMPTY;
    }
}
