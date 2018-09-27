package io.muun.common.bitcoinj;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;

public class NetworkParametersHelper {

    public static final String MAIN_BITCOIN_NETWORK_NAME = "mainnet";

    public static final String BITCOIN_TESTNET3_NETWORK_NAME = "testnet3";

    /**
     * Returns an instance of NetworkParameters from a name.
     *
     * <p>See the different constants of this class to know the available names.
     *
     * @throws RuntimeException if the name is not recognized.
     */
    public static NetworkParameters getNetworkParametersFromName(String name) {
        if (name.equals(MAIN_BITCOIN_NETWORK_NAME)) {
            return MainNetParams.get();
        }

        if (name.equals(BITCOIN_TESTNET3_NETWORK_NAME)) {
            return TestNet3Params.get();
        }

        throw new RuntimeException("Unrecognized network parameters' name: " + name);
    }

    /**
     * Returns the instance of NetworkParameters which corresponds with the base58 serialization of
     * a key.
     */
    public static NetworkParameters getNetworkParametersForBase58Key(String base58Key) {

        if (base58Key.startsWith("xprv") || base58Key.startsWith("xpub")) {
            return MainNetParams.get();
        }

        if (base58Key.startsWith("tprv") || base58Key.startsWith("tpub")) {
            return TestNet3Params.get();
        }

        throw new IllegalArgumentException("Invalid base58 key: " + base58Key);
    }

    /**
     * Returns true if the network parameters corresponds to a testing network.
     */
    public static boolean isTestingNetwork(NetworkParameters networkParameters) {
        return networkParameters instanceof TestNet3Params;
    }

    /**
     * Returns the number of confirmations that a transaction needs to be considered settled.
     */
    public static int getSettlementNumber(NetworkParameters networkParameters) {

        if (networkParameters instanceof MainNetParams) {
            return 6;
        }

        return 50;
    }

}
