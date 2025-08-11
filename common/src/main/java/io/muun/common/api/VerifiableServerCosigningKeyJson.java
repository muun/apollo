package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifiableServerCosigningKeyJson {

    @NotNull
    public String ephemeralPublicKey;

    @NotNull
    public String paddedServerCosigningKey;

    @NotNull
    public String proof;

    public VerifiableServerCosigningKeyJson() {

    }

    public VerifiableServerCosigningKeyJson(
            @Nonnull String ephemeralPublicKey,
            @Nonnull String paddedServerCosigningKey,
            @Nonnull String proof
    ) {
        this.ephemeralPublicKey = ephemeralPublicKey;
        this.paddedServerCosigningKey = paddedServerCosigningKey;
        this.proof = proof;
    }
}
