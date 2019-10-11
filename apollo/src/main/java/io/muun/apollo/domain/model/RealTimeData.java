package io.muun.apollo.domain.model;

import javax.validation.constraints.NotNull;

public class RealTimeData {

    @NotNull
    public final FeeWindow feeWindow;

    @NotNull
    public final ExchangeRateWindow exchangeRateWindow;

    @NotNull
    public final int currentBlockchainHeight;

    /**
     * Constructor.
     */
    public RealTimeData(FeeWindow feeWindow,
                        ExchangeRateWindow exchangeRateWindow,
                        int currentBlockchainHeight) {
        this.feeWindow = feeWindow;
        this.exchangeRateWindow = exchangeRateWindow;
        this.currentBlockchainHeight = currentBlockchainHeight;
    }
}
