package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ForwardingPolicyJson {

    @NotNull
    public String identityKeyHex;

    public long feeBaseMsat;

    public long feeProportionalMillionths;

    public long cltvExpiryDelta;

    /**
     * Json constructor.
     */
    public ForwardingPolicyJson() {
    }

    /**
     * Houston constructor.
     */
    public ForwardingPolicyJson(final String identityKeyHex,
                                final long feeBaseMsat,
                                final long feeProportionalMillionths,
                                final long cltvExpiryDelta) {
        this.identityKeyHex = identityKeyHex;
        this.feeBaseMsat = feeBaseMsat;
        this.feeProportionalMillionths = feeProportionalMillionths;
        this.cltvExpiryDelta = cltvExpiryDelta;
    }
}
