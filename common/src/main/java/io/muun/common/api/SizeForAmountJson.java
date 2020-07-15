package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SizeForAmountJson {

    @NotNull
    public Long amountInSatoshis;

    @NotNull
    public Long sizeInBytes;

    @NotNull
    public String outpoint;

    /**
     * Json constructor.
     */
    public SizeForAmountJson() {
    }

    /**
     * Houston constructor.
     */
    public SizeForAmountJson(Long amountInSatoshis, Long sizeInBytes, String outpoint) {
        this.amountInSatoshis = amountInSatoshis;
        this.sizeInBytes = sizeInBytes;
        this.outpoint = outpoint;
    }
}
