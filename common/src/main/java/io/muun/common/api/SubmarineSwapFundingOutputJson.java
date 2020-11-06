package io.muun.common.api;

import io.muun.common.model.DebtType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapFundingOutputJson {

    @NotNull
    public String outputAddress;

    @Nullable // Null if the invoice didn't have an amount
    public Long outputAmountInSatoshis;

    @Nullable // Null if the invoice didn't have an amount
    public Integer confirmationsNeeded;

    @Nullable
    // for scheme v102+ the lock time is expressed relative to the confirmation time of the funding
    // transaction, so until that happens, this field isn't known
    public Integer userLockTime;

    @Nullable // should always be present for swaps created after deployment of scheme v102
    public Integer expirationInBlocks;

    @NotNull
    @Deprecated
    // It turns out that the concept of user refund address actually doesn't make sense. There
    // aren't different addresses for the same output depending on who spends it. However, we keep
    // this value since old swaps (version 101) use it to extract the user public key hash needed to
    // craft the funding output script.
    public MuunAddressJson userRefundAddress;

    @Nullable // should always be present for swaps created after deployment of scheme v102
    public PublicKeyJson userPublicKey;

    @Nullable // should always be present for swaps created after deployment of scheme v102
    public PublicKeyJson muunPublicKey;

    @NotNull
    public String serverPaymentHashInHex;

    @NotNull
    public String serverPublicKeyInHex;

    @NotNull
    public Integer scriptVersion;

    @NotNull
    public DebtType debtType;

    @Nullable
    public Long debtAmountInSats;


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
            @Nullable Integer userLockTime,
            @Nullable Integer expirationInBlocks,
            MuunAddressJson userRefundAddress,
            @Nullable PublicKeyJson userPublicKey,
            @Nullable PublicKeyJson muunPublicKey,
            String serverPaymentHashInHex,
            String serverPublicKeyInHex,
            Integer scriptVersion,
            DebtType debtType,
            @Nullable Long debtAmountInSats) {

        this.outputAddress = outputAddress;
        this.outputAmountInSatoshis = outputAmountInSatoshis;
        this.confirmationsNeeded = confirmationsNeeded;
        this.userLockTime = userLockTime;
        this.expirationInBlocks = expirationInBlocks;
        this.userRefundAddress = userRefundAddress;
        this.userPublicKey = userPublicKey;
        this.muunPublicKey = muunPublicKey;
        this.serverPaymentHashInHex = serverPaymentHashInHex;
        this.serverPublicKeyInHex = serverPublicKeyInHex;
        this.scriptVersion = scriptVersion;
        this.debtType = debtType;
        this.debtAmountInSats = debtAmountInSats;
    }
}
