package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.SortedMap;
import java.util.TreeMap;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TargetFeeRatesJson {

    @NotNull
    public SortedMap<Integer, Double> confTargetToTargetFeeRateInSatPerVbyte = new TreeMap<>();

    @NotNull
    public Integer fastConfTarget;

    @NotNull
    public Integer mediumConfTarget;

    @NotNull
    public Integer slowConfTarget;

    @NotNull
    public Integer zeroConfSwapConfTarget;

    /**
     * Json constructor.
     */
    public TargetFeeRatesJson() {
    }

    /**
     * Houston constructor.
     */
    public TargetFeeRatesJson(
            SortedMap<Integer, Double> confTargetToTargetFeeRateInSatPerVbyte,
            Integer fastConfTarget,
            Integer mediumConfTarget,
            Integer slowConfTarget,
            Integer zeroConfSwapConfTarget
    ) {

        this.confTargetToTargetFeeRateInSatPerVbyte = confTargetToTargetFeeRateInSatPerVbyte;
        this.fastConfTarget = fastConfTarget;
        this.mediumConfTarget = mediumConfTarget;
        this.slowConfTarget = slowConfTarget;
        this.zeroConfSwapConfTarget = zeroConfSwapConfTarget;
    }
}
