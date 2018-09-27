package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKMULTISIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUAL;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;

/**
 * Version 2 of the Muun transaction scheme.
 * Uses standard Pay-to-Script-Hash multisig 2-of-2 scripts for inputs and outputs. The first key
 * belongs to the User receiving money, the second to Houston.
 */
public class TransactionSchemeV2 {

    public static final int CLIENT_VERSION = 13;
    public static final int ADDRESS_VERSION = MuunAddress.VERSION_COSIGNED_P2SH;

    /**
     * Create an address.
     */
    public static MuunAddress createAddress(PublicKeyPair publicKeyPair) {
        final NetworkParameters network = publicKeyPair.getNetworkParameters();

        final Script redeemScript = createRedeemScript(publicKeyPair);
        final byte[] addressHash160 = getScriptHash(redeemScript);

        return new MuunAddress(
                ADDRESS_VERSION,
                publicKeyPair.getAbsoluteDerivationPath(),
                org.bitcoinj.core.Address.fromP2SHHash(network, addressHash160).toBase58()
        );
    }

    /**
     * Create an input script.
     */
    public static Script createInputScript(PublicKeyPair publicKeyPair,
                                           Signature userSignature,
                                           Signature muunSignature) {

        final Script redeemScript = createRedeemScript(publicKeyPair);

        return new ScriptBuilder()
                .smallNum(0) // if this OP_0 confuses you, ask somebody. Funny stories guaranteed.
                .data(userSignature.getBytes())
                .data(muunSignature.getBytes())
                .data(redeemScript.getProgram())
                .build();
    }

    /**
     * Create an output script, given the user address.
     */
    public static Script createOutputScript(MuunAddress userAddress) {
        return new ScriptBuilder()
                .op(OP_HASH160)
                .data(userAddress.getHash160())
                .op(OP_EQUAL)
                .build();
    }

    /**
     * Create the hash of a simplified form of a Transaction, ready to be signed, for a specific
     * input index.
     */
    public static byte[] createDataToSignInput(Transaction transaction,
                                               int inputIndex,
                                               PublicKeyPair publicKeyPair) {

        return TransactionHelpers.getDataToSign(
                transaction.disableWitnessSerialization(),
                inputIndex,
                createRedeemScript(publicKeyPair)
        );
    }

    private static Script createRedeemScript(PublicKeyPair publicKeyPair) {
        return new ScriptBuilder()
                .smallNum(2) // required signatures
                .data(publicKeyPair.getUserPublicKey().getPublicKeyBytes())
                .data(publicKeyPair.getMuunPublicKey().getPublicKeyBytes())
                .smallNum(2) // total signatures
                .op(OP_CHECKMULTISIG)
                .build();
    }

    private static byte[] getScriptHash(Script script) {
        return Hashes.sha256Ripemd160(script.getProgram());
    }
}
