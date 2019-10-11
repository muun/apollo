package io.muun.common.bitcoinj;

import org.bitcoinj.params.TestNet3Params;

public class TestNetParamsV extends TestNet3Params {

    private static TestNetParamsV instance;

    /**
     * Get an instance.
     */
    public static synchronized TestNetParamsV get() {
        if (instance == null) {
            instance = new TestNetParamsV();
        }
        return instance;
    }

    private TestNetParamsV() {
        super();
        id = "org.bitcoin.test.p2wpkh";
        bip32HeaderP2WPKHpub = 0x045f1cf6;
        bip32HeaderP2WPKHpriv = 0x045f18bc;
    }
}
