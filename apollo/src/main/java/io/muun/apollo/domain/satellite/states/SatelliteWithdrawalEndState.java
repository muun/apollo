package io.muun.apollo.domain.satellite.states;

import io.muun.apollo.domain.model.BitcoinAmount;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteWithdrawalEndState implements BaseSatelliteState {

    @NotNull
    public String uuid;

    @NotNull
    public BitcoinAmount amount;

    /**
     * Json constructor.
     */
    public SatelliteWithdrawalEndState() {
    }

    /**
     * Apollo constructor.
     */
    public SatelliteWithdrawalEndState(String uuid, BitcoinAmount amount) {
        this.uuid = uuid;
        this.amount = amount;
    }

    @Override
    public SatelliteScreen getScreen() {
        return SatelliteScreen.WITHDRAWAL_END;
    }
}
