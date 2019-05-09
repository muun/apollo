package io.muun.apollo.domain.action;

import io.muun.apollo.domain.utils.FeeCalculator;
import io.muun.common.crypto.hwallet.HardwareWalletState;

public class TrezorActions {

    /**
     * Get available balance.
     */
    public static Long availableBalance(long satoshisPerByte, HardwareWalletState state) {
        final FeeCalculator calculator = new FeeCalculator(
                satoshisPerByte,
                state.getSizeForAmounts()
        );

        return calculator.getMaxSpendableAmount();
    }
}
