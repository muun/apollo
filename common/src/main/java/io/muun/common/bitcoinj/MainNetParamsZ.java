package io.muun.common.bitcoinj;

import org.bitcoinj.params.MainNetParams;

public class MainNetParamsZ extends MainNetParams {

    private static MainNetParamsZ instance;

    /**
     * Get an instance.
     */
    public static synchronized MainNetParamsZ get() {
        if (instance == null) {
            instance = new MainNetParamsZ();
        }
        return instance;
    }

    private MainNetParamsZ() {
        super();
        id = "org.bitcoin.production.p2wpkh";
        bip32HeaderP2WPKHpub = 0x04b24746;
        bip32HeaderP2WPKHpriv = 0x04b2430c;
    }
}
