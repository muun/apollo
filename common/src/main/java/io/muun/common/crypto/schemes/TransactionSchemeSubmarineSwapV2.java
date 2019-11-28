package io.muun.common.crypto.schemes;

import io.muun.common.Supports;
import io.muun.common.api.SubmarineSwapFundingOutputJson;
import io.muun.common.api.SubmarineSwapJson;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.LnInvoice;
import io.muun.common.utils.Preconditions;
import io.muun.common.utils.Since;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSEQUENCEVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIGVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_DROP;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_ELSE;
import static org.bitcoinj.script.ScriptOpCodes.OP_ENDIF;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUAL;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;
import static org.bitcoinj.script.ScriptOpCodes.OP_IF;
import static org.bitcoinj.script.ScriptOpCodes.OP_SWAP;

/**
 * A TransactionScheme to generate SubmarineSwap scripts, refund addresses and spending inputs.
 */
@Since(
        apolloVersion = Supports.SubmarineSwapsV2.APOLLO,
        falconVersion = Supports.SubmarineSwapsV2.FALCON
)
public class TransactionSchemeSubmarineSwapV2 {

    public static final int ADDRESS_VERSION = MuunAddress.VERSION_SUBMARINE_SWAP_V2;

    // per bip 68 (the one where relative lock-time is defined)
    private static final int MAX_RELATIVE_LOCK_TIME_BLOCKS = 0xFFFF;

    /**
     * Create an address.
     */
    public static Address createAddress(NetworkParameters network, byte[] witnessScript) {

        final byte[] witnessScriptHash = Sha256Hash.hash(witnessScript);
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

        final byte[] witnessScriptHash = Sha256Hash.hash(witnessScript);

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
                                               long amount,
                                               byte[] witnessScript) {

        return TransactionHelpers.getSegwitDataToSign(
                transaction,
                inputIndex,
                new Script(witnessScript),
                Coin.valueOf(amount)
        );
    }

    /**
     * Create the witness for spending the submarine swap in case the swap expires, even if the
     * server in unwilling to collaborate.
     */
    public static TransactionWitness createWitnessForUser(PublicKey muunPublicKey,
                                                          Signature muunSignature,
                                                          Signature userSignature,
                                                          byte[] witnessScript) {

        final TransactionWitness witness = new TransactionWitness(5);
        witness.setPush(0, muunSignature.getBytes());
        witness.setPush(1, muunPublicKey.getPublicKeyBytes());
        witness.setPush(2, userSignature.getBytes());
        witness.setPush(3, new byte[0]);
        witness.setPush(4, witnessScript);
        return witness;
    }

    /**
     * Create the witness for spending the submarine swap in case the payment fails, and the user
     * and the server are willing to collaborate.
     */
    public static TransactionWitness createWitnessForCollaboration(Signature userSignature,
                                                                   Signature swapServerSignature,
                                                                   byte[] witnessScript) {

        final TransactionWitness witness = new TransactionWitness(3);
        witness.setPush(0, userSignature.getBytes());
        witness.setPush(1, swapServerSignature.getBytes());
        witness.setPush(2, witnessScript);
        return witness;
    }

    /**
     * Create the witness for spending the submarine swap in case the server gets hold of the
     * payment secret in time.
     */
    public static TransactionWitness createWitnessForSwapServer(byte[] swapPaymentSecret,
                                                                Signature swapServerSignature,
                                                                byte[] witnessScript) {

        final TransactionWitness witness = new TransactionWitness(3);
        witness.setPush(0, swapPaymentSecret);
        witness.setPush(1, swapServerSignature.getBytes());
        witness.setPush(2, witnessScript);
        return witness;
    }

    /**
     * Create the witness script for spending the submarine swap output.
     */
    public static byte[] createWitnessScript(byte[] swapPaymentHash256,
                                             byte[] userPublicKey,
                                             byte[] muunPublicKey,
                                             byte[] swapServerPublicKey,
                                             int numBlocksForExpiration) {

        Preconditions.checkArgument(numBlocksForExpiration <= MAX_RELATIVE_LOCK_TIME_BLOCKS);

        // It turns out that the payment hash present in an invoice is just the SHA256 of the
        // payment preimage, so we still have to do a pass of RIPEMD160 before pushing it to the
        // script
        final byte[] swapPaymentHash160 = Hashes.ripemd160(swapPaymentHash256);
        final byte[] serverPublicKeyHash160 = Hashes.sha256Ripemd160(muunPublicKey);

        // Equivalent miniscript (http://bitcoin.sipa.be/miniscript/):
        // or(
        //   and(pk(userPublicKey), pk(swapServerPublicKey)),
        //   or(
        //     and(pk(swapServerPublicKey), hash160(swapPaymentHash160)),
        //     and(pk(userPublicKey), and(pk(muunPublicKey), older(numBlocksForExpiration)))
        //   )
        // )
        //
        // However, we differ in that the size of the script was heavily optimized for spending the
        // first two branches (the collaborative close and the unilateral close by swapper), which
        // are the most probable to be used.

        return new ScriptBuilder()
                // Push the user public key to the second position of the stack
                .data(userPublicKey)
                .op(OP_SWAP)

                // Check whether the first stack item was a valid swap server signature
                .data(swapServerPublicKey)
                .op(OP_CHECKSIG)

                // If the swap server signature was correct
                .op(OP_IF)
                    .op(OP_SWAP)

                    // Check whether the second stack item was the payment preimage
                    .op(OP_DUP)
                    .op(OP_HASH160)
                    .data(swapPaymentHash160)
                    .op(OP_EQUAL)

                    // If the preimage was correct
                    .op(OP_IF)
                        // We are done, leave just one true-ish item in the stack (there're 2
                        // remaining items)
                        .op(OP_DROP)

                    // If the second stack item wasn't a valid payment preimage
                    .op(OP_ELSE)
                        // Validate that the second stack item was a valid user signature
                        .op(OP_SWAP)
                        .op(OP_CHECKSIG)

                    .op(OP_ENDIF)

                // If the first stack item wasn't a valid server signature
                .op(OP_ELSE)
                    // Validate that the blockchain height is big enough
                    .number(numBlocksForExpiration)
                    .op(OP_CHECKSEQUENCEVERIFY)
                    .op(OP_DROP)

                    // Validate that the second stack item was a valid user signature
                    .op(OP_CHECKSIGVERIFY)

                    // Validate that the third stack item was the muun public key
                    .op(OP_DUP)
                    .op(OP_HASH160)
                    .data(serverPublicKeyHash160)
                    .op(OP_EQUALVERIFY)
                    // Notice that instead of directly pushing the public key here and checking the
                    // signature P2PK-style, we pushed the hash of the public key, and require an
                    // extra stack item with the actual public key, verifying the signature and
                    // public key P2PKH-style.
                    //
                    // This trick reduces the on-chain footprint of the muun key from 33 bytes to
                    // 20 bytes for the collaborative, and swap server's non-collaborative branches,
                    // which are the most frequent ones.

                    // Validate that the fourth stack item was a valid server signature
                    .op(OP_CHECKSIG)

                .op(OP_ENDIF)
                .build()
                .getProgram();
    }

    /**
     * Validate Submarine Swap Server response. The end goal is to verify that the redeem script
     * returned by the server is the script that is actually encoded in the reported swap address.
     */
    public static boolean validateSwap(String originalInvoice,
                                       Integer originalExpirationInBlocks,
                                       PublicKeyPair userPublicKeyPair,
                                       SubmarineSwapJson swapJson,
                                       NetworkParameters network) {

        final SubmarineSwapFundingOutputJson fundingOutput = swapJson.fundingOutput;

        // Check to avoid handling older swaps (e.g Swaps V1). With every swap version upgrade,
        // there will always be a window of time where newer clients can receive a previously
        // created swap with an older version (scanning same ln invoice of an already created swap).
        // We decided to save a lot of trouble and code and not support this edge case. This check
        // (and this comment) makes this decision EXPLICIT :)
        Preconditions.checkArgument(
                fundingOutput.scriptVersion == TransactionSchemeSubmarineSwapV2.ADDRESS_VERSION
        );

        // Check that the embedded invoice is the same as the original
        if (!originalInvoice.equalsIgnoreCase(swapJson.invoice)) {
            return false;
        }

        // Parse invoice
        final LnInvoice invoice = LnInvoice.decode(network, originalInvoice);

        // Check that the receiver is the same as the original
        if (!invoice.destinationPubKey.equals(swapJson.receiver.publicKey)) {
            return false;
        }

        // Check that the payment hash matches the invoice
        if (!invoice.id.equals(fundingOutput.serverPaymentHashInHex)) {
            return false;
        }

        if (!originalExpirationInBlocks.equals(fundingOutput.expirationInBlocks)) {
            return false;
        }

        final PublicKey userPublicKey = PublicKey.fromJson(fundingOutput.userPublicKey);
        final PublicKey muunPublicKey = PublicKey.fromJson(fundingOutput.muunPublicKey);

        final PublicKeyPair derivedPublicKeyPair = userPublicKeyPair
                .deriveFromAbsolutePath(fundingOutput.userPublicKey.path);

        // Check that the user public key belongs to the user
        if (!derivedPublicKeyPair.getUserPublicKey().equals(userPublicKey)) {
            return false;
        }

        // Check that the muun public key belongs to muun
        if (!derivedPublicKeyPair.getMuunPublicKey().equals(muunPublicKey)) {
            return false;
        }

        final String paymentHashInHex = fundingOutput.serverPaymentHashInHex;

        // Check that the witness script was computed according to the given parameters
        final byte[] witnessScript = createWitnessScript(
                Encodings.hexToBytes(paymentHashInHex),
                userPublicKey.getPublicKeyBytes(),
                muunPublicKey.getPublicKeyBytes(),
                Encodings.hexToBytes(fundingOutput.serverPublicKeyInHex),
                fundingOutput.expirationInBlocks
        );

        // Check that the script hashes to the output address we'll be using
        final Address outputAddress = createAddress(network, witnessScript);

        if (!outputAddress.toString().equals(fundingOutput.outputAddress)) {
            return false;
        }

        // Check other values for internal consistency
        final String preimageInHex = swapJson.preimageInHex;
        if (preimageInHex != null) {

            final byte[] calculatedHash = Hashes.sha256(Encodings.hexToBytes(preimageInHex));
            if (!paymentHashInHex.equals(Encodings.bytesToHex(calculatedHash))) {
                return false;
            }
        }

        return true;
    }
}
