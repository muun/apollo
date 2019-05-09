package io.muun.common.crypto.hd;

import java.util.List;

public class HardwareWalletAddress {

    private final String address;
    private final String derivationPath;

    /**
     * Constructor.
     */
    public HardwareWalletAddress(String address, String derivationPath) {
        this.address = address;
        this.derivationPath = derivationPath;
    }


    public String getAddress() {
        return address;
    }

    public String getDerivationPath() {
        return derivationPath;
    }

    /**
     * Get the last derivation index of this address' path.
     */
    public int getIndex() {
        final List<ChildNumber> childNumbers = DerivationPathUtils.parsePath(derivationPath);
        final ChildNumber childNumber = childNumbers.get(childNumbers.size() - 1);

        return childNumber.getIndex();
    }

    @Override public String toString() {
        return "Address: " + address + ", Derivation Path: " + derivationPath;
    }
}
