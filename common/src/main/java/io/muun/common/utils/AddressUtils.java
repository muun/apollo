package io.muun.common.utils;

import io.muun.common.crypto.hd.PrivateKey;

import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.DeterministicKey;

public class AddressUtils {

    /**
     * Generate a random address.
     */
    public static org.bitcoinj.core.Address getRandomAddress(NetworkParameters networkParams) {

        final DeterministicKey deterministicKey = PrivateKey.getNewRootPrivateKey(networkParams)
                .getDeterministicKey();

        return LegacyAddress.fromKey(networkParams, deterministicKey);
    }
}
