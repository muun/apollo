package io.muun.common.api;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MuunInputSubmarineSwapV101Json {

    @NotNull
    public String refundAddress;

    @NotNull
    public String swapPaymentHash256Hex;

    @NotNull
    public String swapServerPublicKeyHex;

    @NotNull
    public Long lockTime;

    /**
     * Json constructor.
     */
    public MuunInputSubmarineSwapV101Json() {
    }

    /**
     * Manual constructor.
     */
    public MuunInputSubmarineSwapV101Json(String refundAddress,
                                          String swapPaymentHash256Hex,
                                          String swapServerPublicKeyHex,
                                          Long lockTime) {

        this.refundAddress = refundAddress;
        this.swapPaymentHash256Hex = swapPaymentHash256Hex;
        this.swapServerPublicKeyHex = swapServerPublicKeyHex;
        this.lockTime = lockTime;
    }
}
