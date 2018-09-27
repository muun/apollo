package io.muun.common.crypto.hd;


import org.bitcoinj.core.NetworkParameters;

import javax.validation.constraints.NotNull;

public class MuunAddress {

    public static final int VERSION_P2PKH = 1;
    public static final int VERSION_COSIGNED_P2SH = 2;
    public static final int VERSION_COSIGNED_P2SH_P2WSH = 3;

    /**
     * Return the TransactionScheme version used in new Addresses by default.
     */
    public static int getDefaultVersion() {
        return VERSION_COSIGNED_P2SH;
    }

    private final int version;

    @NotNull
    private final String derivationPath;

    @NotNull
    private final String address;

    /**
     * Constructor.
     */
    public MuunAddress(int version, String derivationPath, String address) {
        this.version = version;
        this.derivationPath = derivationPath;
        this.address = address;
    }

    /**
     * Constructor with default address version.
     */
    public MuunAddress(String derivationPath, String address) {
        this.version = getDefaultVersion();
        this.derivationPath = derivationPath;
        this.address = address;
    }

    public int getVersion() {
        return version;
    }

    public String getDerivationPath() {
        return derivationPath;
    }

    public String getAddress() {
        return address;
    }

    public byte[] getHash160() {
        return toBitcoinJ().getHash160();
    }

    public NetworkParameters getNetwork() {
        return toBitcoinJ().getParameters();
    }

    private org.bitcoinj.core.Address toBitcoinJ() {
        // NOTE: we provide `null` NetworkParameters. The information is already contained in the
        // base58 serialization, and we don't need to validate it.
        return org.bitcoinj.core.Address.fromBase58(null, address);
    }
}
