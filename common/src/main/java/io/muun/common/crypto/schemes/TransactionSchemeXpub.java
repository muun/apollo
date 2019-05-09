package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;

public class TransactionSchemeXpub {

    /**
     * Create an address.
     */
    public static Address createAddress(PublicKey userPublicKey) {
        return Address.fromBase58(userPublicKey.networkParameters, userPublicKey.toAddress());
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
    public static Script createOutputScript(Address userAddress) {
        return new ScriptBuilder()
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(userAddress.getHash160())
                .op(OP_EQUALVERIFY)
                .op(OP_CHECKSIG)
                .build();
    }

    /**
     * Create an empty witness.
     */
    public static TransactionWitness createWitness() {

        return TransactionWitness.getEmpty();
    }

    /**
     * Create the hash of a simplified form of a Transaction, ready to be signed, for a specific
     * input index.
     */
    public static byte[] createDataToSignInput(Transaction transaction,
                                               int inputIndex,
                                               PublicKey publicKey) {

        final Address userAddress = createAddress(publicKey);

        final Script spentOutputScript = createOutputScript(userAddress);

        return TransactionHelpers.getDataToSign(
                transaction.disableWitnessSerialization(),
                inputIndex,
                spentOutputScript
        );
    }


    /**
     * Given a script, obtains its hash in big indian.
     */
    private static String obtainScriptHash(Script script) {
        return ByteUtils.toHexString(reverse(Hashes.sha256(script.getProgram())));
    }

    /**
     * Given a Public key, get its scriptHash.
     */
    public static String getScriptHash(PublicKey publicKey) {
        return obtainScriptHash(createOutputScript(createAddress(publicKey)));
    }

    private static byte[] reverse(byte[] arr) {
        final int length = arr.length;
        final byte[] reversed = new byte[length];
        for (int i = 0; i < length; i++) {
            reversed[i] = arr[length - i - 1];
        }
        return reversed;
    }
}
