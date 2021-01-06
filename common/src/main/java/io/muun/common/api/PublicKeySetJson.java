package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicKeySetJson {

    @NotNull
    public PublicKeyJson basePublicKey;

    @Nullable
    public PublicKeyJson baseCosigningPublicKey;

    @Nullable
    public PublicKeyJson baseSwapServerPublicKey;

    @Nullable
    public ExternalAddressesRecord externalPublicKeyIndices;

    /**
     * Json constructor.
     */
    public PublicKeySetJson() {
    }

    /**
     * Apollo update constructor.
     */
    public PublicKeySetJson(PublicKeyJson basePublicKey) {
        this.basePublicKey = basePublicKey;
    }

    /**
     * Apollo IntegrityCheck constructor.
     */
    public PublicKeySetJson(PublicKeyJson basePublicKey,
                            ExternalAddressesRecord externalPublicKeyIndices) {

        this.basePublicKey = basePublicKey;
        this.externalPublicKeyIndices = externalPublicKeyIndices;
    }

    /**
     * Houston constructor.
     */
    public PublicKeySetJson(PublicKeyJson basePublicKey,
                            PublicKeyJson baseCosigningPublicKey,
                            PublicKeyJson baseSwapServerPublicKey,
                            ExternalAddressesRecord externalPublicKeyIndices) {
        this.basePublicKey = basePublicKey;
        this.baseCosigningPublicKey = baseCosigningPublicKey;
        this.baseSwapServerPublicKey = baseSwapServerPublicKey;
        this.externalPublicKeyIndices = externalPublicKeyIndices;
    }
}
