package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapFundingOutputPoliciesJson {

    @NotNull
    public Long maximumDebtInSat;

    @NotNull
    public Long potentialCollectInSat;

    @NotNull
    public Long maxAmountInSatFor0Conf;

    /**
     * JSON constructor.
     */
    public SubmarineSwapFundingOutputPoliciesJson() {
    }

    /**
     * Houston constructor.
     */
    public SubmarineSwapFundingOutputPoliciesJson(
            final Long maximumDebtInSat,
            final Long potentialCollectInSat,
            final Long maxAmountInSatFor0Conf) {
        this.maximumDebtInSat = maximumDebtInSat;
        this.potentialCollectInSat = potentialCollectInSat;
        this.maxAmountInSatFor0Conf = maxAmountInSatFor0Conf;
    }
}
