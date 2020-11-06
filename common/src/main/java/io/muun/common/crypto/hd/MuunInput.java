package io.muun.common.crypto.hd;


import io.muun.common.api.MuunInputJson;

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
    private MuunInputSubmarineSwapV101 submarineSwap; // submarine swap V101 refund inputs only

    @Nullable
    private MuunInputSubmarineSwapV102 submarineSwapV102; // submarine swap V102 refund inputs only

    @Nullable
    private MuunInputIncomingSwap incomingSwap; // for incoming swap inputs only

    /**
     * Build from a json-serializable representation.
     */
    public static MuunInput fromJson(MuunInputJson json) {

        return new MuunInput(
                MuunOutput.fromJson(json.prevOut),
                MuunAddress.fromJson(json.address),
                json.userSignature == null ? null : Signature.fromJson(json.userSignature),
                json.muunSignature == null ? null : Signature.fromJson(json.muunSignature),
                json.submarineSwap == null ? null : MuunInputSubmarineSwapV101.fromJson(
                        json.submarineSwap
                ),
                json.submarineSwapV102 == null ? null : MuunInputSubmarineSwapV102.fromJson(
                        json.submarineSwapV102
                ),
                json.incomingSwap == null ? null : MuunInputIncomingSwap.fromJson(json.incomingSwap)
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
                     @Nullable MuunInputSubmarineSwapV101 submarineSwap,
                     @Nullable MuunInputSubmarineSwapV102 submarineSwapV102,
                     @Nullable MuunInputIncomingSwap incomingSwap) {

        this.prevOut = prevOut;
        this.address = address;
        this.userSignature = userSignature;
        this.muunSignature = muunSignature;
        this.submarineSwap = submarineSwap;
        this.submarineSwapV102 = submarineSwapV102;
        this.incomingSwap = incomingSwap;
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

    public void setUserSignature(Signature userSignature) {
        this.userSignature = userSignature;
    }

    public void setMuunSignature(Signature muunSignature) {
        this.muunSignature = muunSignature;
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

    /**
     * Convert to a json-serializable representation.
     */
    public MuunInputJson toJson() {

        return new MuunInputJson(
                prevOut.toJson(),
                address.toJson(),
                userSignature == null ? null : userSignature.toJson(),
                muunSignature == null ? null : muunSignature.toJson(),
                submarineSwap == null ? null : submarineSwap.toJson(),
                submarineSwapV102 == null ? null : submarineSwapV102.toJson(),
                incomingSwap == null ? null : incomingSwap.toJson()
        );
    }

    public long getLockTime() {
        return submarineSwap != null ? submarineSwap.getLockTime() : 0;
    }
}
