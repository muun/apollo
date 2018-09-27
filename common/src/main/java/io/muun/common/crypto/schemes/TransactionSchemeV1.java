package io.muun.common.crypto.schemes;


import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;

/**
 * Version 1 of the Muun transaction scheme.
 * Uses standard Pay-to-Public-Key-Hash scripts for inputs and outputs.
 */
public class TransactionSchemeV1 {

    public static final int CLIENT_VERSION = 1;
    public static final int ADDRESS_VERSION = MuunAddress.VERSION_P2PKH;

    /**
     * Create an address.
     */
    public static MuunAddress createAddress(NetworkParameters network, PublicKey userPublicKey) {
        return new MuunAddress(
                ADDRESS_VERSION,
                userPublicKey.getAbsoluteDerivationPath(),
                userPublicKey.toAddress()
        );
    }

    /**
     * Create an input script.
     */
    public static Script createInputScript(PublicKey userPublicKey, Signature signature) {
        return new ScriptBuilder()
                .data(signature.getBytes())
                .data(userPublicKey.getPublicKeyBytes())
                .build();
    }

    /**
     * Create an output script.
     */
    public static Script createOutputScript(MuunAddress userAddress) {
        return new ScriptBuilder()
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(userAddress.getHash160())
                .op(OP_EQUALVERIFY)
                .op(OP_CHECKSIG)
                .build();
    }

    /**
     * Create the hash of a simplified form of a Transaction, ready to be signed, for a specific
     * input index.
     */
    public static byte[] createDataToSignInput(Transaction transaction,
                                               int inputIndex,
                                               PublicKey publicKey) {

        final NetworkParameters network = transaction.getParams();

        final MuunAddress userAddress = createAddress(network, publicKey);

        final Script spentOutputScript = createOutputScript(userAddress);

        return TransactionHelpers.getDataToSign(
                transaction.disableWitnessSerialization(),
                inputIndex,
                spentOutputScript
        );
    }
}
