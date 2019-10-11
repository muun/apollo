package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;

public class TransactionSchemeYpub {

    /**
     * Creates an address from a public key.
     */
    public static Address createAddress(PublicKey publicKey) {

        final Script redeemScript = createRedeemScript(publicKey);
        final byte[] addressHash160 = Hashes.sha256Ripemd160(redeemScript.getProgram());

        return LegacyAddress.fromScriptHash(publicKey.networkParameters, addressHash160);
    }

    /**
     * Creates a redeem script from a public key.
     */
    public static Script createRedeemScript(PublicKey publicKey) {

        return new ScriptBuilder()
                .smallNum(0)
                .data(Hashes.sha256Ripemd160(publicKey.getPublicKeyBytes()))
                .build();
    }

    /**
     * Create input script.
     */
    public static Script createInputScript(PublicKey publicKey) {

        final Script redeemScript = createRedeemScript(publicKey);

        return new ScriptBuilder()
                .data(redeemScript.getProgram())
                .build();
    }

    /**
     * Given an address, creates a script pub key.
     */
    public static Script createOutputScript(Address address) {

        return new ScriptBuilder()
                .op(ScriptOpCodes.OP_HASH160)
                .data(address.getHash())
                .op(ScriptOpCodes.OP_EQUAL)
                .build();
    }

    /**
     * Create the witness structure, according to our signing scheme.
     * Namely, p2sh(p2pkh).
     */
    public static TransactionWitness createWitness(PublicKey publicKey,
                                                   Signature signature) {

        // This correlates to TransactionSchemeV2 inputScript
        final TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, signature.getBytes());
        witness.setPush(1, publicKey.getPublicKeyBytes());

        return witness;
    }

    /**
     * Create the hash of a simplified form of a Transaction, ready to be signed, for a specific
     * input index.
     */
    public static byte[] createDataToSignInput(Transaction transaction,
                                               int inputIndex,
                                               long amount,
                                               PublicKey publicKey) {

        final Script embedded = new ScriptBuilder()
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(Hashes.sha256Ripemd160(publicKey.getPublicKeyBytes()))
                .op(OP_EQUALVERIFY)
                .op(OP_CHECKSIG)
                .build();

        final Script scriptCode = new ScriptBuilder()
                .data(embedded.getProgram())
                .build();

        return TransactionHelpers.getSegwitDataToSign(
                transaction,
                inputIndex,
                scriptCode,
                Coin.valueOf(amount)
        );
    }

    /**
     * Given a script, obtains its hash in big indian.
     */
    public static String obtainScriptHash(Script script) {
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
