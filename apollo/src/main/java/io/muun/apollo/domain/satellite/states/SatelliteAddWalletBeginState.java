package io.muun.apollo.domain.satellite.states;

import io.muun.common.model.HardwareWalletBrand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteAddWalletBeginState implements BaseSatelliteState {

    public HardwareWalletBrand brand;

    /**
     * Json constructor.
     */
    public SatelliteAddWalletBeginState() {
    }

    /**
     * Apollo constructor.
     */
    public SatelliteAddWalletBeginState(HardwareWalletBrand brand) {
        this.brand = brand;
    }

    @Override
    public SatelliteScreen getScreen() {
        return SatelliteScreen.ADD_WALLET_BEGIN;
    }
}
