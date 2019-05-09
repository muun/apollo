package io.muun.common.crypto.hwallet;

import io.muun.common.crypto.hd.HardwareWalletAddress;
import io.muun.common.crypto.hd.HardwareWalletOutput;
import io.muun.common.model.SizeForAmount;

import java.util.List;

import javax.money.MonetaryAmount;

public class ExtendedHardwareWalletState extends HardwareWalletState {

    /**
     * Extend an existing state.
     */
    public static ExtendedHardwareWalletState from(HardwareWalletState state,
                                                   MonetaryAmount balanceInPrimary) {
        return new ExtendedHardwareWalletState(
                state.sortedOutputs,
                state.sizeForAmounts,
                state.changeAddress,
                state.nextAddress,
                balanceInPrimary
        );
    }

    private final MonetaryAmount balanceInPrimary;

    /**
     * Constructor.
     */
    public ExtendedHardwareWalletState(List<HardwareWalletOutput> sortedOutputs,
                                       List<SizeForAmount> sizeForAmounts,
                                       HardwareWalletAddress changeAddress,
                                       HardwareWalletAddress nextAddress,
                                       MonetaryAmount balanceInPrimary) {

        super(sortedOutputs, sizeForAmounts, changeAddress, nextAddress);
        this.balanceInPrimary = balanceInPrimary;
    }

    public MonetaryAmount getBalanceInPrimary() {
        return balanceInPrimary;
    }
}
