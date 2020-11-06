package io.muun.common.api;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MuunInputJson {

    @NotNull
    public MuunOutputJson prevOut;

    @NotNull
    public MuunAddressJson address;

    @Nullable
    public SignatureJson userSignature;

    @Nullable
    public SignatureJson muunSignature;

    @Nullable
    public MuunInputSubmarineSwapV101Json submarineSwap;

    @Nullable
    public MuunInputSubmarineSwapV102Json submarineSwapV102;

    @Nullable
    public MuunInputIncomingSwapJson incomingSwap;

    /**
     * Json constructor.
     */
    public MuunInputJson() {
    }

    /**
     * Manual constructor.
     */
    public MuunInputJson(MuunOutputJson prevOut,
                         MuunAddressJson address,
                         @Nullable SignatureJson userSignature,
                         @Nullable SignatureJson muunSignature,
                         @Nullable MuunInputSubmarineSwapV101Json submarineSwap,
                         @Nullable MuunInputSubmarineSwapV102Json submarineSwapV102,
                         @Nullable MuunInputIncomingSwapJson incomingSwap) {

        this.prevOut = prevOut;
        this.address = address;
        this.userSignature = userSignature;
        this.muunSignature = muunSignature;
        this.submarineSwap = submarineSwap;
        this.submarineSwapV102 = submarineSwapV102;
        this.incomingSwap = incomingSwap;
    }
}
