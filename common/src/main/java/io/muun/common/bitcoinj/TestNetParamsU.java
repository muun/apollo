package io.muun.common.bitcoinj;

import org.bitcoinj.params.TestNet3Params;

public class TestNetParamsU extends TestNet3Params {

    private static TestNetParamsU instance;

    /**
     * Get an instance.
     */
    public static synchronized TestNetParamsU get() {
        if (instance == null) {
            instance = new TestNetParamsU();
        }
        return instance;
    }

    private TestNetParamsU() {
        super();
        id = "org.bitcoin.test.ps2h(p2wpkh)";
        bip32HeaderP2PKHpub = 0x044a5262;
        bip32HeaderP2PKHpriv = 0x044a4e28;
    }
}
