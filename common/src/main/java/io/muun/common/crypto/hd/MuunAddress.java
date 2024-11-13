package io.muun.common.crypto.hd;


import io.muun.common.api.MuunAddressJson;

import org.bitcoinj.core.Address;

import javax.validation.constraints.NotNull;

public class MuunAddress {

    public static final int VERSION_P2PKH = 1;
    public static final int VERSION_COSIGNED_P2SH = 2;
    public static final int VERSION_COSIGNED_P2SH_P2WSH = 3;
    public static final int VERSION_COSIGNED_P2WSH = 4;
    public static final int VERSION_COSIGNED_P2TR = 5;
    public static final int VERSION_FUNDING_P2SH_P2WSH = 6; // reserved
    public static final int VERSION_FUNDING_P2WSH = 7;

    public static final int VERSION_SUBMARINE_SWAP_V1 = 101;
    public static final int VERSION_SUBMARINE_SWAP_V2 = 102;

    public static final int VERSION_INCOMING_SWAP = 201;

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
     * Create a MuunAddress model from its Json counterpart.
     */
    public static MuunAddress fromJson(MuunAddressJson json) {
        if (json == null) {
            return null;
        }

        return new MuunAddress(json.version, json.derivationPath, json.address);
    }

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

    public byte[] getHash() {
        return toBitcoinJ().getHash();
    }

    @SuppressWarnings({"DanglingJavadoc", "MissingJavadocMethod"})
    /**
     * Deprecated and commented out as a deterrent. This method "falls short" for regtest addresses:
     * as it uses the base58 serialization to read the data, it does not distinguish between
     * testnet and regtest addresses. Leaving the code commented in case someone in the future fixes
     * or tries to fix this.
     */
    //public NetworkParameters getNetwork() {
    //    return toBitcoinJ().getParameters();
    //}

    /**
     * Map this model to BitcoinJ's model.
     */
    public org.bitcoinj.core.Address toBitcoinJ() {
        // NOTE: we provide `null` NetworkParameters. The information is already contained in the
        // base58 serialization, and we don't need to validate it.
        return Address.fromString(null, address);
    }

    /**
     * Map this model to json.
     */
    public MuunAddressJson toJson() {
        return new MuunAddressJson(version, derivationPath, address);
    }
}
