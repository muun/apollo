package io.muun.common.crypto.hd;


import io.muun.common.api.MuunInputJson;
import io.muun.common.utils.Encodings;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class MuunInput {

    @NotNull
    private final MuunOutput prevOut;

    @NotNull
    private final MuunAddress address;

    @Nullable
    private Signature userSignature;

    @Nullable
    private Signature muunSignature; // co-signed inputs only

    @Nullable
    private Signature swapServerSignature; // channel inputs only

    @Nullable
    private MuunInputSubmarineSwapV101 submarineSwap; // submarine swap V101 refund inputs only

    @Nullable
    private MuunInputSubmarineSwapV102 submarineSwapV102; // submarine swap V102 refund inputs only

    @Nullable
    private MuunInputIncomingSwap incomingSwap; // for incoming swap inputs only

    @Nullable
    private byte[] rawUserPublicNonce; // musig inputs only. Set by user.

    @Nullable
    private byte[] rawMuunPublicNonce; // musig inputs only. Set by houston.

    // NOTE: exists only for testing capabilities. DO NOT EVER assume its existence or try to use it
    @Nullable
    private byte[] userSessionId; // musig inputs only

    /**
     * Build from a json-serializable representation.
     */
    public static MuunInput fromJson(MuunInputJson json) {

        return new MuunInput(
                MuunOutput.fromJson(json.prevOut),
                MuunAddress.fromJson(json.address),
                json.userSignature == null ? null : Signature.fromJson(json.userSignature),
                json.muunSignature == null ? null : Signature.fromJson(json.muunSignature),
                json.swapServerSignature == null ? null : Signature.fromJson(
                        json.swapServerSignature
                ),
                json.submarineSwap == null ? null : MuunInputSubmarineSwapV101.fromJson(
                        json.submarineSwap
                ),
                json.submarineSwapV102 == null ? null : MuunInputSubmarineSwapV102.fromJson(
                        json.submarineSwapV102
                ),
                json.incomingSwap == null ? null : MuunInputIncomingSwap.fromJson(
                        json.incomingSwap
                ),
                json.rawMuunPublicNonceHex == null ? null : Encodings.hexToBytes(
                        json.rawMuunPublicNonceHex
                )
        );
    }

    /**
     * Constructor without signatures or additional details.
     */
    public MuunInput(MuunOutput prevOut, MuunAddress address) {
        this.prevOut = prevOut;
        this.address = address;
    }

    /**
     * Full constructor.
     */
    public MuunInput(MuunOutput prevOut,
                     MuunAddress address,
                     @Nullable Signature userSignature,
                     @Nullable Signature muunSignature,
                     @Nullable Signature swapServerSignature,
                     @Nullable MuunInputSubmarineSwapV101 submarineSwap,
                     @Nullable MuunInputSubmarineSwapV102 submarineSwapV102,
                     @Nullable MuunInputIncomingSwap incomingSwap,
                     @Nullable byte[] rawMuunPublicNonce) {

        this.prevOut = prevOut;
        this.address = address;
        this.userSignature = userSignature;
        this.muunSignature = muunSignature;
        this.swapServerSignature = swapServerSignature;
        this.submarineSwap = submarineSwap;
        this.submarineSwapV102 = submarineSwapV102;
        this.incomingSwap = incomingSwap;
        this.rawMuunPublicNonce = rawMuunPublicNonce;
    }

    public MuunOutput getPrevOut() {
        return prevOut;
    }

    public int getVersion() {
        return address.getVersion();
    }

    public String getDerivationPath() {
        return address.getDerivationPath();
    }

    public MuunAddress getAddress() {
        return address;
    }

    @Nullable
    public Signature getUserSignature() {
        return userSignature;
    }

    @Nullable
    public Signature getMuunSignature() {
        return muunSignature;
    }

    @Nullable
    public Signature getSwapServerSignature() {
        return swapServerSignature;
    }

    public void setUserSignature(Signature userSignature) {
        this.userSignature = userSignature;
    }

    public void setMuunSignature(Signature muunSignature) {
        this.muunSignature = muunSignature;
    }

    public void setSwapServerSignature(Signature swapServerSignature) {
        this.swapServerSignature = swapServerSignature;
    }

    @Nullable
    public MuunInputSubmarineSwapV101 getSubmarineSwap() {
        return submarineSwap;
    }

    public void setSubmarineSwap(@Nullable MuunInputSubmarineSwapV101 submarineSwap) {
        this.submarineSwap = submarineSwap;
    }

    @Nullable
    public MuunInputSubmarineSwapV102 getSubmarineSwapV102() {
        return submarineSwapV102;
    }

    public void setSubmarineSwapV102(@Nullable MuunInputSubmarineSwapV102 submarineSwapV102) {
        this.submarineSwapV102 = submarineSwapV102;
    }

    @Nullable
    public MuunInputIncomingSwap getIncomingSwap() {
        return incomingSwap;
    }

    public void setIncomingSwap(@Nullable MuunInputIncomingSwap incomingSwap) {
        this.incomingSwap = incomingSwap;
    }

    @Nullable
    public byte[] getRawUserPublicNonce() {
        return rawUserPublicNonce;
    }

    public void setRawMuunPublicNonce(@Nullable byte[] rawMuunPublicNonce) {
        this.rawMuunPublicNonce = rawMuunPublicNonce;
    }

    @Nullable
    public byte[] getRawMuunPublicNonce() {
        return rawMuunPublicNonce;
    }

    public void setRawUserPublicNonce(@Nullable byte[] rawUserPublicNonce) {
        this.rawUserPublicNonce = rawUserPublicNonce;
    }

    @Nullable
    public byte[] getUserSessionId() {
        return userSessionId;
    }

    public void setUserSessionId(@Nullable byte[] userSessionId) {
        this.userSessionId = userSessionId;
    }

    /**
     * Convert to a json-serializable representation.
     */
    public MuunInputJson toJson() {

        return new MuunInputJson(
                prevOut.toJson(),
                address.toJson(),
                userSignature == null ? null : userSignature.toJson(),
                muunSignature == null ? null : muunSignature.toJson(),
                swapServerSignature == null ? null : swapServerSignature.toJson(),
                submarineSwap == null ? null : submarineSwap.toJson(),
                submarineSwapV102 == null ? null : submarineSwapV102.toJson(),
                incomingSwap == null ? null : incomingSwap.toJson(),
                rawMuunPublicNonce == null ? null : Encodings.bytesToHex(rawMuunPublicNonce)
        );
    }

    public long getLockTime() {
        return submarineSwap != null ? submarineSwap.getLockTime() : 0;
    }
}
