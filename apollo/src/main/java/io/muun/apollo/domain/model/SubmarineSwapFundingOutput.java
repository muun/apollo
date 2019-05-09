package io.muun.apollo.domain.model;

import io.muun.common.crypto.hd.MuunAddress;

public class SubmarineSwapFundingOutput {

    public final String outputAddress;
    public final Long outputAmountInSatoshis;
    public final Integer confirmationsNeeded;
    public final Integer userLockTime;
    public final MuunAddress userRefundAddress;
    public final String serverPaymentHashInHex;
    public final String serverPublicKeyInHex;

    /**
     * Constructor.
     */
    public SubmarineSwapFundingOutput(
            String outputAddress,
            Long outputAmountInSatoshis,
            Integer confirmationsNeeded,
            Integer userLockTime,
            MuunAddress userRefundAddress,
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
