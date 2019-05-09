package io.muun.common.bitcoinj;

import io.muun.common.exception.MissingCaseError;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;

public class NetworkParametersHelper {

    private static final String MAIN_BITCOIN_NETWORK_NAME = "mainnet";

    private static final String BITCOIN_TESTNET3_NETWORK_NAME = "testnet3";

    private static final String BITCOIN_REGTEST_NETWORK_NAME = "regtest";

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

        if (name.equals(BITCOIN_REGTEST_NETWORK_NAME)) {
            return RegTestParams.get();
        }

        throw new RuntimeException("Unrecognized network parameters' name: " + name);
    }

    /**
     * Returns the instance of NetworkParameters which corresponds with the base58 serialization of
     * a key.
     */
    public static NetworkParameters getNetworkParametersForBase58Key(String base58Key) {

        // If you are wondering what the heck is going on here:
        // https://github.com/satoshilabs/slips/blob/master/slip-0132.md

        if (base58Key.startsWith("xprv") || base58Key.startsWith("xpub")) {
            return MainNetParams.get();
        }

        if (base58Key.startsWith("yprv") || base58Key.startsWith("ypub")) {
            return MainNetParamsY.get();
        }

        if (base58Key.startsWith("zprv") || base58Key.startsWith("zpub")) {
            return MainNetParamsZ.get();
        }

        if (base58Key.startsWith("tprv") || base58Key.startsWith("tpub")) {
            return TestNet3Params.get();
        }

        if (base58Key.startsWith("uprv") || base58Key.startsWith("upub")) {
            return TestNetParamsU.get();
        }

        if (base58Key.startsWith("vprv") || base58Key.startsWith("vpub")) {
            return TestNetParamsV.get();
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

        } else if (networkParameters instanceof TestNet3Params) {
            return 50;

        } else if (networkParameters instanceof RegTestParams) {
            return 6;

        } else {
            throw new MissingCaseError(networkParameters, "Network parameters for settlement num");
        }
    }

}
