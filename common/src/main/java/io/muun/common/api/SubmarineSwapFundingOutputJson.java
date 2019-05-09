package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapFundingOutputJson {

    @NotNull
    public String outputAddress;

    @NotNull
    public Long outputAmountInSatoshis;

    @NotNull
    public Integer confirmationsNeeded;

    @NotNull
    public Integer userLockTime;

    @NotNull
    public MuunAddressJson userRefundAddress;

    @NotNull
    public String serverPaymentHashInHex;

    @NotNull
    public String serverPublicKeyInHex;

    /**
     * Json constructor.
     */
    public SubmarineSwapFundingOutputJson() {
    }

    /**
     * Constructor.
     */
    public SubmarineSwapFundingOutputJson(
            String outputAddress,
            Long outputAmountInSatoshis,
            Integer confirmationsNeeded,
            Integer userLockTime,
            MuunAddressJson userRefundAddress,
            String serverPaymentHashInHex,
            String serverPublicKeyInHex) {

        this.outputAddress = outputAddress;
        this.outputAmountInSatoshis = outputAmountInSatoshis;
        this.confirmationsNeeded = confirmationsNeeded;
        this.userLockTime = userLockTime;
        this.userRefundAddress = userRefundAddress;
        this.serverPaymentHashInHex = serverPaymentHashInHex;
        this.serverPublicKeyInHex = serverPublicKeyInHex;
    }
}
