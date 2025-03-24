package io.muun.common.api;


import io.muun.common.Supports;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReplaceByFeeJson {

    @NotNull
    public BitcoinAmountJson fee;

    @NotNull
    public List<String> outpoints;

    // TODO: This is not integrated into apollo or falcon yet.
    @Since(apolloVersion = Supports.Taproot.APOLLO, falconVersion = Supports.Taproot.FALCON)
    @Nullable // For old clients, never set
    public List<String> userPublicNoncesHex;

    /**
     * Json Constructor.
     */
    public ReplaceByFeeJson() {
    }

    /**
     * Constructor.
     */
    public ReplaceByFeeJson(
            BitcoinAmountJson fee,
            List<String> outpoints,
            List<String> userPublicNoncesHex
    ) {
        this.fee = fee;
        this.outpoints = outpoints;
        this.userPublicNoncesHex = userPublicNoncesHex;
    }
}
