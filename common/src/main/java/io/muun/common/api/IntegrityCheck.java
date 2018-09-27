package io.muun.common.api;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrityCheck {

    @NotNull
    public PublicKeySetJson publicKeySet;

    @NotNull
    public Long balanceInSatoshis;

    /**
     * JSON constructor.
     */
    public IntegrityCheck() {
    }

    /**
     * Manual constructor.
     */
    public IntegrityCheck(PublicKeySetJson publicKeySet, Long balance) {
        this.publicKeySet = publicKeySet;
        this.balanceInSatoshis = balance;
    }
}
