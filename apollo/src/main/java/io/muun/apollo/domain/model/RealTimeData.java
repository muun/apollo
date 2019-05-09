package io.muun.apollo.domain.model;

import javax.validation.constraints.NotNull;

public class RealTimeData {

    @NotNull
    public final FeeWindow feeWindow;

    @NotNull
    public final ExchangeRateWindow exchangeRateWindow;

    public RealTimeData(FeeWindow feeWindow, ExchangeRateWindow exchangeRateWindow) {
        this.feeWindow = feeWindow;
        this.exchangeRateWindow = exchangeRateWindow;
    }
}
