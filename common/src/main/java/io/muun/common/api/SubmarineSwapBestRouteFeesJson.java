package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapBestRouteFeesJson {

    @NotNull
    public Long maxCapacityInSat;

    @NotNull
    public Long proportionalMillionth;

    @NotNull
    public Long baseInSat;

    /**
     * JSON constructor.
     */
    public SubmarineSwapBestRouteFeesJson() {
    }

    /**
     * Houston constructor.
     */
    public SubmarineSwapBestRouteFeesJson(final Long maxCapacityInSat,
                                          final Long proportionalMillionth,
                                          final Long baseInSat) {
        this.maxCapacityInSat = maxCapacityInSat;
        this.proportionalMillionth = proportionalMillionth;
        this.baseInSat = baseInSat;
    }
}
