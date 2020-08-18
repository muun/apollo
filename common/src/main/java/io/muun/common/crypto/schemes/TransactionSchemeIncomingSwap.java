package io.muun.common.crypto.schemes;

import io.muun.common.Supports;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;
import io.muun.common.utils.Since;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIGVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_ELSE;
import static org.bitcoinj.script.ScriptOpCodes.OP_ENDIF;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUAL;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;
import static org.bitcoinj.script.ScriptOpCodes.OP_NOTIF;
import static org.bitcoinj.script.ScriptOpCodes.OP_SIZE;

/**
 * A TransactionScheme to generate segwit native incoming swap scripts and spending inputs.
 */
@Since(
        apolloVersion = Supports.IncomingSwaps.APOLLO,
        falconVersion = Supports.IncomingSwaps.FALCON
)
public class TransactionSchemeIncomingSwap {

    public static final int ADDRESS_VERSION = MuunAddress.VERSION_INCOMING_SWAP;

    /**
     * Create an address.
     */
    public static Address createAddress(NetworkParameters network, byte[] witnessScript) {

        final byte[] witnessScriptHash = Hashes.sha256(witnessScript);
        return SegwitAddress.fromHash(network, witnessScriptHash);
    }

    /**
     * Create the (empty) input script.
     */
    public static Script createInputScript(byte[] witnessScript) {
        return ScriptBuilder.createEmpty();
    }

    /**
     * Create the output script.
     */
    public static Script createOutputScript(byte[] witnessScript) {

        final byte[] witnessScriptHash = Hashes.sha256(witnessScript);

        return new ScriptBuilder()
                .smallNum(0)
                .data(witnessScriptHash)
                .build();
    }

    /**
     * Create the hash of a simplified form of a Transaction, ready to be signed, for a specific
     * input index.
     */
    public static byte[] createDataToSignInput(Transaction transaction,
                                               int inputIndex,
                                               long amountInSat,
                                               byte[] witnessScript) {

        return TransactionHelpers.getSegwitDataToSign(
                transaction,
                inputIndex,
                new Script(witnessScript),
                Coin.valueOf(amountInSat)
        );
    }

    /**
     * Create the witness for spending when the user claims the swap by revealing the preimage.
     *
     * <p>This here strictly for backend testing since this apps will use libwallet
     */
    public static TransactionWitness createWitnessForUser(byte[] preimage,
                                                          Signature muunSignature,
                                                          Signature userSignature,
                                                          byte[] witnessScript) {

        Preconditions.checkArgument(
                preimage.length == 32, "preimage has to be 32 bytes"
        );

        final TransactionWitness witness = new TransactionWitness(4);
        witness.setPush(0, preimage);
        witness.setPush(1, userSignature.getBytes());
        witness.setPush(2, muunSignature.getBytes());
        witness.setPush(3, witnessScript);
        return witness;
    }

    /**
     * Create the witness for spending by the swap server when the swap expires.
     */
    public static TransactionWitness createWitnessForSwapServer(PublicKey swapServerPubKey,
                                                                Signature swapServerSignature,
                                                                byte[] witnessScript) {

        final TransactionWitness witness = new TransactionWitness(4);
        witness.setPush(0, swapServerPubKey.getPublicKeyBytes());
        witness.setPush(1, swapServerSignature.getBytes());
        witness.setPush(2, new byte[0]);
        witness.setPush(3, witnessScript);
        return witness;
    }

    /**
     * Create the witness script for spending the submarine swap output.
     */
    public static byte[] createWitnessScript(byte[] swapPaymentHash256,
                                             byte[] userPublicKey,
                                             byte[] muunPublicKey,
                                             byte[] swapServerPublicKey,
                                             int expirationHeight) {

        Preconditions.checkArgument(
                swapPaymentHash256.length == 32, "payment hash to be 32 bytes"
        );

        Preconditions.checkPositive(expirationHeight);

        final byte[] swapPaymentHash160 = Hashes.ripemd160(swapPaymentHash256);
        final byte[] swapServerPublicKeyHash160 = Hashes.sha256Ripemd160(swapServerPublicKey);

        // Equivalent miniscript (http://bitcoin.sipa.be/miniscript/):
        // or(
        //   and(pk(muunPublicKey), and(pk(userPublicKey), hash160(swapPaymentHash160))),
        //   and(pk(swapServerPublicKey), after(expirationHeight)))
        // )
        //
        // We assume the swap server spend won't be common. So in that branch we replace the
        // swap server pub key with it's HASH160 and require the full key is part of the witness
        // stack for those spends.

        return new ScriptBuilder()
                // Check whether the first stack item is a valid muun signature
                .data(muunPublicKey)
                .op(OP_CHECKSIG)

                // If it was not a valid muun signature
                .op(OP_NOTIF)
                    // The second stack item should be the swap server key, dup it to verify it
                    .op(OP_DUP)

                    // Verify whether the second stack item is the swap server key
                    .op(OP_HASH160)
                    .data(swapServerPublicKeyHash160)
                    .op(OP_EQUALVERIFY)

                    // Notice that instead of directly pushing the public key here and checking the
                    // signature P2PK-style, we pushed the hash of the public key, and require an
                    // extra stack item with the actual public key, verifying the signature and
                    // public key P2PKH-style.
                    //
                    // This trick reduces the on-chain footprint of the swap server key from 33
                    // bytes to 20 bytes.

                    // Effectively check the swap server signature
                    .op(OP_CHECKSIGVERIFY)

                    // Validate that the TX has enough confirmations
                    .number(expirationHeight)
                    .op(OP_CHECKLOCKTIMEVERIFY)

                // If the first stack item was a valid muun signature
                .op(OP_ELSE)
                    // Validate that the second stack item was a valid user signature
                    .data(userPublicKey)
                    .op(OP_CHECKSIGVERIFY)

                    // Check the third stack item is 20 bytes long
                    .op(OP_SIZE)
                    .number(20)
                    .op(OP_EQUALVERIFY)

                    // Check the third stack item hashes to the given payment hash
                    .op(OP_HASH160)
                    .data(swapPaymentHash160)
                    .op(OP_EQUAL)

                .op(OP_ENDIF)
                .build()
                .getProgram();
    }

}
