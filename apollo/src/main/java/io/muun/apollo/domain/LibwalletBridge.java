package io.muun.apollo.domain;

import io.muun.apollo.domain.errors.LibwalletMismatchAddressError;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.schemes.TransactionSchemeV1;
import io.muun.common.crypto.schemes.TransactionSchemeV2;
import io.muun.common.crypto.schemes.TransactionSchemeV3;
import io.muun.common.crypto.schemes.TransactionSchemeV4;

import libwallet.HDPublicKey;
import libwallet.Libwallet;
import libwallet.Network;
import org.bitcoinj.core.NetworkParameters;
import timber.log.Timber;

public class LibwalletBridge {

    /**
     * Create a V1 MuunAddress.
     */
    public static MuunAddress createAddressV1(PublicKey pubKey, NetworkParameters params) {

        final MuunAddress address = TransactionSchemeV1.createAddress(pubKey);

        final HDPublicKey userKey = toLibwalletModel(pubKey, params);

        final libwallet.MuunAddress addressV1 = createAddressV1(userKey);

        if (addressV1 != null && !address.getAddress().equals(addressV1.address())) {
            Timber.e(new LibwalletMismatchAddressError(address.getAddress(), addressV1.address()));
        }

        return address;
    }

    private static libwallet.MuunAddress createAddressV1(HDPublicKey userKey) {
        try {
            return Libwallet.createAddressV1(userKey);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    /**
     * Create a V2 MuunAddress.
     */
    public static MuunAddress createAddressV2(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final MuunAddress address = TransactionSchemeV2.createAddress(pubKeyPair, params);

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        final libwallet.MuunAddress addressV2 = createAddressV2(userKey, muunKey);

        if (addressV2 != null && !address.getAddress().equals(addressV2.address())) {
            Timber.e(new LibwalletMismatchAddressError(address.getAddress(), addressV2.address()));
        }

        return address;
    }

    private static libwallet.MuunAddress createAddressV2(HDPublicKey userKey, HDPublicKey muunKey) {
        try {
            return Libwallet.createAddressV2(userKey, muunKey);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    /**
     * Create a V3 MuunAddress.
     */
    public static MuunAddress createAddressV3(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final MuunAddress address = TransactionSchemeV3.createAddress(pubKeyPair, params);

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        final libwallet.MuunAddress addressV3 = createAddressV3(userKey, muunKey);

        if (addressV3 != null && !address.getAddress().equals(addressV3.address())) {
            Timber.e(new LibwalletMismatchAddressError(address.getAddress(), addressV3.address()));
        }

        return address;
    }

    private static libwallet.MuunAddress createAddressV3(HDPublicKey userKey, HDPublicKey muunKey) {
        try {
            return Libwallet.createAddressV3(userKey, muunKey);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    /**
     * Create a V4 MuunAddress.
     */
    public static MuunAddress createAddressV4(PublicKeyPair pubKeyPair, NetworkParameters params) {

        final MuunAddress address = TransactionSchemeV4.createAddress(pubKeyPair, params);

        final HDPublicKey userKey = toLibwalletModel(pubKeyPair.getUserPublicKey(), params);
        final HDPublicKey muunKey = toLibwalletModel(pubKeyPair.getMuunPublicKey(), params);

        final libwallet.MuunAddress addressV4 = createAddressV4(userKey, muunKey);

        if (addressV4 != null && !address.getAddress().equals(addressV4.address())) {
            Timber.e(new LibwalletMismatchAddressError(address.getAddress(), addressV4.address()));
        }

        return address;
    }

    private static libwallet.MuunAddress createAddressV4(HDPublicKey userKey, HDPublicKey muunKey) {
        try {
            return Libwallet.createAddressV4(userKey, muunKey);

        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    private static HDPublicKey toLibwalletModel(PublicKey pubKey, NetworkParameters params) {
        return new HDPublicKey(
                pubKey.serializeBase58(),
                pubKey.getAbsoluteDerivationPath(),
                toLibwalletModel(params)
        );
    }

    private static Network toLibwalletModel(NetworkParameters networkParameters) {
        if (NetworkParameters.ID_MAINNET.equals(networkParameters.getId())) {
            return Libwallet.mainnet();

        } else if (NetworkParameters.ID_REGTEST.equals(networkParameters.getId())) {
            return Libwallet.regtest();

        } else {
            return Libwallet.testnet();
        }
    }
}
