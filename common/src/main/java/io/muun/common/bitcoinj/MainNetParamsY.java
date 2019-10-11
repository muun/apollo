package io.muun.common.bitcoinj;

import org.bitcoinj.params.MainNetParams;

public class MainNetParamsY extends MainNetParams {

    private static MainNetParamsY instance;

    /**
     * Get an instance.
     */
    public static synchronized MainNetParamsY get() {
        if (instance == null) {
            instance = new MainNetParamsY();
        }
        return instance;
    }

    private MainNetParamsY() {
        super();
        id = "org.bitcoin.production.ps2h(p2wpkh)";
        bip32HeaderP2PKHpub = 0x049d7cb2;
        bip32HeaderP2PKHpriv = 0x049d7878;
    }
}