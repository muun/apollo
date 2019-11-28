package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MuunInputSubmarineSwapV102Json {

    @NotNull
    public String swapPaymentHash256Hex;

    @NotNull
    public String userPublicKeyHex;

    @NotNull
    public String muunPublicKeyHex;

    @NotNull
    public String swapServerPublicKeyHex;

    @NotNull
    public Integer numBlocksForExpiration;

    @Nullable
    public SignatureJson swapServerSignature;

    /**
     * Json constructor.
     */
    public MuunInputSubmarineSwapV102Json() {
    }

    /**
     * Manual constructor.
     */
    public MuunInputSubmarineSwapV102Json(
            String swapPaymentHash256Hex,
            String userPublicKeyHex,
            String muunPublicKeyHex,
            String swapServerPublicKeyHex,
            Integer numBlocksForExpiration,
            @Nullable SignatureJson swapServerSignature) {

        this.swapPaymentHash256Hex = swapPaymentHash256Hex;
        this.userPublicKeyHex = userPublicKeyHex;
        this.muunPublicKeyHex = muunPublicKeyHex;
        this.swapServerPublicKeyHex = swapServerPublicKeyHex;
        this.numBlocksForExpiration = numBlocksForExpiration;
        this.swapServerSignature = swapServerSignature;
    }
}
