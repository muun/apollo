package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RealTimeData {

    @NotNull
    public FeeWindowJson feeWindow;

    @NotNull
    public ExchangeRateWindow exchangeRateWindow;

    @Since(
            apolloVersion = Supports.BlockchainHeight.APOLLO,
            falconVersion = Supports.BlockchainHeight.FALCON
    )
    public int currentBlockchainHeight;

    /**
     * Json constructor.
     */
    public RealTimeData() {
    }

    /**
     * Houston constructor.
     */
    public RealTimeData(FeeWindowJson feeWindow,
                        ExchangeRateWindow exchangeRateWindow,
                        int currentBlockchainHeight) {
        this.feeWindow = feeWindow;
        this.exchangeRateWindow = exchangeRateWindow;
        this.currentBlockchainHeight = currentBlockchainHeight;
    }
}
