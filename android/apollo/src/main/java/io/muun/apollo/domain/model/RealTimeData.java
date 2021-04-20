package io.muun.apollo.domain.model;

import java.util.List;
import javax.validation.constraints.NotNull;

public class RealTimeData {

    @NotNull
    public final FeeWindow feeWindow;

    @NotNull
    public final ExchangeRateWindow exchangeRateWindow;

    @NotNull
    public final int currentBlockchainHeight;

    @NotNull
    public final List<ForwardingPolicy> forwardingPolicies;

    @NotNull
    public final double minFeeRateInWeightUnits;

    /**
     * Constructor.
     */
    public RealTimeData(final FeeWindow feeWindow,
                        final ExchangeRateWindow exchangeRateWindow,
                        final int currentBlockchainHeight,
                        final List<ForwardingPolicy> forwardingPolicies,
                        double minFeeRateInWeightUnits) {
        this.feeWindow = feeWindow;
        this.exchangeRateWindow = exchangeRateWindow;
        this.currentBlockchainHeight = currentBlockchainHeight;
        this.forwardingPolicies = forwardingPolicies;
        this.minFeeRateInWeightUnits = minFeeRateInWeightUnits;
    }
}
