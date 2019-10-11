package io.muun.common.crypto.tx;

import io.muun.common.Optional;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptOpCodes;

import java.util.List;

import javax.annotation.Nullable;

public final class TransactionHelpers {

    public static final int SIGHASH_ALL = 0x01;

    private TransactionHelpers() {
        throw new AssertionError();
    }

    /**
     * Get the signature message for a Transaction's input.
     */
    public static byte[] getDataToSign(Transaction transaction,
                                       int inputIndex,
                                       Script replacementScript) {
        return transaction.hashForSignature(inputIndex, replacementScript, SigHash.ALL, false)
                .getBytes();
    }

    /**
     * Get the signature message for a Transaction's input, according to segwit.
     * (See BIP143: https://github.com/bitcoin/bips/blob/master/bip-0143.mediawiki)
     */
    public static byte[] getSegwitDataToSign(Transaction tx,
                                             int inputIndex,
                                             Script witnessScript,
                                             Coin value) {
        return tx.hashForWitnessSignature(inputIndex, witnessScript, value, SigHash.ALL, false)
                .getBytes();
    }

    /**
     * Compute a transaction hash that is independent of malleability (ie. malleated transactions
     * get the same hash value).
     *
     * <p>We build the transaction with the same inputs and outputs, but without the input scripts,
     * and return the hash of this transaction.
     */
    public static String getNonMalleableHash(Transaction tx) {

        final Transaction txClone = new Transaction(
                tx.getParams(),
                tx.unsafeBitcoinSerialize()
        );

        for (TransactionInput input : txClone.getInputs()) {
            input.setScriptSig(new Script(new byte[]{}));
        }

        return txClone.getTxId().toString();
    }

    /**
     * Get the hex-encoded raw transaction.
     */
    public static String getHexRawTransaction(Transaction tx) {

        return Encodings.bytesToHex(tx.unsafeBitcoinSerialize());
    }

    /**
     * Deserialize a transaction from its hex encoding.
     */
    public static Transaction getTransactionFromHexRaw(NetworkParameters network, String hexTx) {

        return new Transaction(network, Encodings.hexToBytes(hexTx));
    }

    /**
     * Get the destination address from a given output.
     */
    public static String getAddressFromOutput(TransactionOutput output) {

        final Address address = extractAddressFromOutput(output).orElse(null);
        return address != null ? address.toString() : null;
    }

    /**
     * Extract an address from an output.
     *
     * <p>We only support P2PKH and P2SH addresses. Neither the old P2PK addresses nor the new
     * P2WPKH/P2WSH addresses are supported.
     */
    public static Optional<Address> extractAddressFromOutput(TransactionOutput output) {

        final Script script = getOutputScript(output);
        if (script == null) {
            return Optional.empty();
        }

        final List<ScriptChunk> chunks = script.getChunks();
        final NetworkParameters network = output.getParams();

        if (isP2PkhOutput(chunks)) {
            final byte[] hash = chunks.get(2).data;
            return Optional.of(LegacyAddress.fromPubKeyHash(network, hash));
        }

        if (isP2ShOutput(chunks)) {
            final byte[] hash = chunks.get(1).data;
            return Optional.of(LegacyAddress.fromScriptHash(network, hash));
        }

        return Optional.empty();
    }

    @Nullable
    private static Script getOutputScript(TransactionOutput output) {
        try {
            return output.getScriptPubKey();
        } catch (ScriptException e) {
            return null; // bitcoinj sometimes fails to parse non-standard outputs
        }
    }

    /**
     * Decide whether a script is a P2PKH output script.
     */
    private static boolean isP2PkhOutput(List<ScriptChunk> chunks) {

        // A P2PKH output script is exactly:
        // OP_DUP OP_HASH160 <public key hash> OP_EQUALVERIFY OP_CHECKSIG

        return chunks.size() == 5
                && chunks.get(0).equalsOpCode(ScriptOpCodes.OP_DUP)
                && chunks.get(1).equalsOpCode(ScriptOpCodes.OP_HASH160)
                && chunks.get(2).data != null
                && chunks.get(2).data.length == LegacyAddress.LENGTH
                && chunks.get(3).equalsOpCode(ScriptOpCodes.OP_EQUALVERIFY)
                && chunks.get(4).equalsOpCode(ScriptOpCodes.OP_CHECKSIG);
    }

    /**
     * Decide whether a script is a P2SH output script.
     */
    private static boolean isP2ShOutput(List<ScriptChunk> chunks) {

        // A P2SH output script is exactly:
        // OP_HASH160 <script hash> OP_EQUAL

        return chunks.size() == 3
                && chunks.get(0).equalsOpCode(ScriptOpCodes.OP_HASH160)
                && chunks.get(1).data != null
                && chunks.get(1).data.length == LegacyAddress.LENGTH
                && chunks.get(2).equalsOpCode(ScriptOpCodes.OP_EQUAL);
    }

    /**
     * Get the spent address from a given P2PKH input.
     */
    public static String getAddressFromInput(TransactionInput input) {

        final Address address = extractAddressFromInput(input).orElse(null);
        return address != null ? address.toString() : null;
    }

    /**
     * Extract optimistically an address from an input. Note that this is a best effort guess and
     * can have false positives.
     *
     * <p>We only support P2PKH and P2SH addresses. Neither the old P2PK addresses nor the new
     * P2WPKH/P2WSH addresses are supported.
     */
    public static Optional<Address> extractAddressFromInput(TransactionInput input) {

        // skip coinbase inputs
        if (input.getOutpoint().getHash().equals(Sha256Hash.ZERO_HASH)) {
            return Optional.empty();
        }

        final Script script = getInputScript(input);
        if (script == null) {
            return Optional.empty();
        }

        final List<ScriptChunk> chunks = script.getChunks();
        final NetworkParameters network = input.getParams();

        if (isP2PkhInput(chunks)) {
            final byte[] rawPubKey = chunks.get(1).data;
            final byte[] addressHash = Hashes.sha256Ripemd160(rawPubKey);

            return Optional.of(LegacyAddress.fromPubKeyHash(network, addressHash));
        }

        if (isP2ShInput(chunks)) {
            final byte[] rawRedeemScript = chunks.get(chunks.size() - 1).data;
            final byte[] addressHash = Hashes.sha256Ripemd160(rawRedeemScript);

            return Optional.of(LegacyAddress.fromScriptHash(network, addressHash));
        }

        return Optional.empty();
    }

    @Nullable
    private static Script getInputScript(TransactionInput input) {
        try {
            return input.getScriptSig();
        } catch (ScriptException e) {
            return null; // bitcoinj sometimes fails to parse non-standard inputs
        }
    }

    /**
     * Decide whether a script is a P2PKH-spending input script. This is a best effort guess.
     */
    private static boolean isP2PkhInput(List<ScriptChunk> chunks) {

        // P2PKH inputs consist of a push of a DER-encoded signature, followed by a push of a public
        // key.

        if (chunks.size() != 2 || !chunks.get(0).isPushData() || !chunks.get(1).isPushData()) {
            return false;
        }

        // A DER-encoded ECDSA signature (with a sig-hash type byte postfix) can measure at most 73
        // bytes, but might be as short as 9 bytes (~50% signatures are 72-bytes long).

        final byte[] signature = chunks.get(0).data;
        if (signature == null || signature.length < 9 || signature.length > 73) {
            return false;
        }

        // A compressed public key measures 33 bytes and an uncompressed one 65 bytes.

        final byte[] publicKey = chunks.get(1).data;
        if (publicKey == null || (publicKey.length != 33 && publicKey.length != 65)) {
            return false;
        }

        return true;
    }

    /**
     * Decide whether a script is a P2SH-spending input script. This is a best effort guess.
     */
    private static boolean isP2ShInput(List<ScriptChunk> chunks) {

        // P2SH inputs consist of a non-zero number of pushes, where the last push has the redeem
        // script.

        if (chunks.size() == 0) {
            return false;
        }

        for (ScriptChunk chunk : chunks) {
            if (!chunk.isPushData()) {
                return false;
            }
        }

        final byte[] redeemScript = chunks.get(chunks.size() - 1).data;
        if (redeemScript == null) {
            return false;
        }

        return true;
    }
}
