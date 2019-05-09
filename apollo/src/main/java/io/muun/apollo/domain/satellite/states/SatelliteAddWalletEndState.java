package io.muun.apollo.domain.satellite.states;

import io.muun.common.api.HardwareWalletJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteAddWalletEndState implements BaseSatelliteState {

    public HardwareWalletJson hardwareWallet;

    /**
     * Json constructor.
     */
    public SatelliteAddWalletEndState() {
    }

    /**
     * Apollo constructor.
     */
    public SatelliteAddWalletEndState(HardwareWalletJson hardwareWallet) {
        this.hardwareWallet = hardwareWallet;
    }

    @Override
    public SatelliteScreen getScreen() {
        return SatelliteScreen.ADD_WALLET_END;
    }
}
