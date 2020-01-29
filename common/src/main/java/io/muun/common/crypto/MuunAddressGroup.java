package io.muun.common.crypto;

import io.muun.common.crypto.hd.MuunAddress;

public class MuunAddressGroup {

    public final MuunAddress legacy;
    public final MuunAddress segwit;

    /**
     * Constructor.
     */
    public MuunAddressGroup(MuunAddress legacy, MuunAddress segwit) {
        this.legacy = legacy;
        this.segwit = segwit;
    }
}
