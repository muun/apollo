package io.muun.common.crypto.hd;


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
    private Signature muunSignature;

    /**
     * Constructor without signatures.
     */
    public MuunInput(MuunOutput prevOut, MuunAddress address) {
        this.prevOut = prevOut;
        this.address = address;
    }

    /**
     * Constructor with signatures.
     */
    public MuunInput(MuunOutput prevOut,
                     MuunAddress address,
                     Signature userSignature,
                     Signature muunSignature) {
        this.prevOut = prevOut;
        this.address = address;
        this.userSignature = userSignature;
        this.muunSignature = muunSignature;
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
}
