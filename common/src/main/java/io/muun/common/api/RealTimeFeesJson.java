package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RealTimeFeesJson {

    /**
     * Each fee bump function is codified as a base64 string.
     * The order of these functions is linked to the list of utxos' outpoints in realtime/fees API.
     * (see {@link RealTimeFeesRequestJson}).
     */
    @NotNull
    public FeeBumpFunctionsJson feeBumpFunctions;

    @NotNull
    public TargetFeeRatesJson targetFeeRates;

    @NotNull
    public double minMempoolFeeRateInSatPerVbyte;

    @NotNull
    public double minFeeRateIncrementToReplaceByFeeInSatPerVbyte;

    @NotNull
    public MuunZonedDateTime computedAt;

    /**
     * Json Constructor.
     */
    public RealTimeFeesJson() {
    }

    /**
     * All args constructor.
     */
    public RealTimeFeesJson(
            FeeBumpFunctionsJson feeBumpFunctions,
            TargetFeeRatesJson targetFeeRates,
            double minMempoolFeeRateInSatPerVbyte,
            double minFeeRateIncrementToReplaceByFeeInSatPerVbyte,
            MuunZonedDateTime computedAt
    ) {

        this.feeBumpFunctions = feeBumpFunctions;
        this.targetFeeRates = targetFeeRates;
        this.minMempoolFeeRateInSatPerVbyte = minMempoolFeeRateInSatPerVbyte;
        this.minFeeRateIncrementToReplaceByFeeInSatPerVbyte =
                minFeeRateIncrementToReplaceByFeeInSatPerVbyte;
        this.computedAt = computedAt;
    }
}