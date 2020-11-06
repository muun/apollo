package io.muun.common.api;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReplaceByFeeJson {

    @NotNull
    public BitcoinAmountJson fee;

    @NotNull
    public List<String> outpoints;

    /**
     * Json Constructor.
     */
    public ReplaceByFeeJson() {
    }

    /**
     * Constructor.
     */
    public ReplaceByFeeJson(BitcoinAmountJson fee,
                            List<String> outpoints) {
        this.fee = fee;
        this.outpoints = outpoints;
    }
}
