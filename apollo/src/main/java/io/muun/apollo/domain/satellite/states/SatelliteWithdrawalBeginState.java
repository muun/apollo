package io.muun.apollo.domain.satellite.states;

import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.trezor.HardwareWalletWithdrawal;
import io.muun.apollo.domain.satellite.withdrawal.SatelliteWithdrawalJson;
import io.muun.common.model.HardwareWalletBrand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteWithdrawalBeginState implements BaseSatelliteState {

    @NotNull
    public String uuid;

    @NotNull
    public HardwareWalletBrand walletBrand;

    @NotNull
    public String walletLabel;

    @NotNull
    public String receiverAddress;

    @NotNull
    public BitcoinAmount amount;

    @NotNull
    public SatelliteWithdrawalJson withdrawalData;

    /**
     * Json constructor.
     */
    public SatelliteWithdrawalBeginState() {
    }

    /**
     * Apollo constructor.
     */
    public SatelliteWithdrawalBeginState(String uuid,
                                         HardwareWalletBrand walletBrand,
                                         String walletLabel,
                                         String receiverAddress,
                                         BitcoinAmount amount,
                                         HardwareWalletWithdrawal withdrawal) {
        this.uuid = uuid;
        this.walletBrand = walletBrand;
        this.walletLabel = walletLabel;
        this.receiverAddress = receiverAddress;
        this.amount = amount;
        this.withdrawalData = SatelliteWithdrawalJson.fromData(withdrawal);
    }

    @Override
    public SatelliteScreen getScreen() {
        return SatelliteScreen.WITHDRAWAL_BEGIN;
    }
}
