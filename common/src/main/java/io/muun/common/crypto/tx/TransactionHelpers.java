package io.muun.common.crypto.tx;

import io.muun.common.Optional;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUAL;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;

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
     * Get the output script for a given address.
     */
    public static byte[] getOutputScriptFromAddress(NetworkParameters network, String rawAddress) {

        final Address address = Address.fromString(network, rawAddress);

        switch (address.getOutputScriptType()) {

            case P2PKH:
                return new ScriptBuilder()
                        .op(OP_DUP)
                        .op(OP_HASH160)
                        .data(address.getHash())
                        .op(OP_EQUALVERIFY)
                        .op(OP_CHECKSIG)
                        .build()
                        .getProgram();

            case P2SH:
                return new ScriptBuilder()
                        .op(OP_HASH160)
                        .data(address.getHash())
                        .op(OP_EQUAL)
                        .build()
                        .getProgram();

            case P2WPKH:
            case P2WSH:
                return new ScriptBuilder()
                        .smallNum(0)
                        .data(address.getHash())
                        .build()
                        .getProgram();

            default:
                throw new IllegalArgumentException("unsupported address type");
        }
    }

    /**
     * Get the destination address from a given output.
     */
    public static String getAddressFromOutput(TransactionOutput output) {

        return extractAddressFromOutput(output)
                .map(Address::toString)
                .orElse(null);
    }

    /**
     * Extract an address from an output.
     *
     * <p>We don't support the old P2PK addresses.
     */
    private static Optional<Address> extractAddressFromOutput(TransactionOutput output) {

        final Script script = getOutputScript(output);
        if (script == null) {
            return Optional.empty();
        }

        final NetworkParameters network = output.getParams();

        if (ScriptPattern.isP2PKH(script)) {
            final byte[] hash = ScriptPattern.extractHashFromP2PKH(script);
            return Optional.of(LegacyAddress.fromPubKeyHash(network, hash));
        }

        // notice that this branch works regardless of what comes after the P2SH-of-*
        if (ScriptPattern.isP2SH(script)) {
            final byte[] hash = ScriptPattern.extractHashFromP2SH(script);
            return Optional.of(LegacyAddress.fromScriptHash(network, hash));
        }

        // notice that this branch works both for P2WPKH and P2WSH
        if (ScriptPattern.isP2WH(script)) {
            final byte[] hash = ScriptPattern.extractHashFromP2WH(script);
            return Optional.of(SegwitAddress.fromHash(network, hash));
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
     * Get the spent address from a given P2PKH input.
     */
    public static String getAddressFromInput(TransactionInput input) {

        return extractAddressFromInput(input)
                .map(Address::toString)
                .orElse(null);
    }

    /**
     * Extract optimistically an address from an input. Note that this is a best effort guess and
     * can have false positives.
     *
     * <p>We only support P2PKH and P2SH addresses. Neither the old P2PK addresses nor the new
     * P2WPKH/P2WSH addresses are supported.
     */
    private static Optional<Address> extractAddressFromInput(TransactionInput input) {

        // skip coinbase inputs
        if (input.getOutpoint().getHash().equals(Sha256Hash.ZERO_HASH)) {
            return Optional.empty();
        }

        final Script script = getInputScript(input);
        if (script == null) {
            return Optional.empty();
        }

        final NetworkParameters network = input.getParams();
        final List<ScriptChunk> chunks = script.getChunks();
        final TransactionWitness witness = input.getWitness();

        if (isP2PkhInput(chunks, witness)) {
            final byte[] rawPubKey = chunks.get(1).data;
            final byte[] addressHash = Hashes.sha256Ripemd160(rawPubKey);

            return Optional.of(LegacyAddress.fromPubKeyHash(network, addressHash));
        }

        if (isP2ShInput(chunks)) {
            final byte[] rawRedeemScript = chunks.get(chunks.size() - 1).data;
            final byte[] addressHash = Hashes.sha256Ripemd160(rawRedeemScript);

            return Optional.of(LegacyAddress.fromScriptHash(network, addressHash));
        }

        if (isP2WpkhInput(chunks, witness)) {
            final byte[] rawPubKey = witness.getPush(witness.getPushCount() - 1);
            final byte[] addressHash = Hashes.sha256Ripemd160(rawPubKey);

            return Optional.of(SegwitAddress.fromHash(network, addressHash));
        }

        if (isP2WshInput(chunks, witness)) {
            final byte[] rawWitnessScript = witness.getPush(witness.getPushCount() - 1);
            final byte[] addressHash = Hashes.sha256(rawWitnessScript);

            return Optional.of(SegwitAddress.fromHash(network, addressHash));
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
     * Decide whether an input spends a P2PKH output. This is a best effort guess.
     */
    private static boolean isP2PkhInput(List<ScriptChunk> chunks, TransactionWitness witness) {

        // P2PKH inputs have no witnesses

        if (witness.getPushCount() > 0) {
            return false;
        }

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
     * Decide whether an input spends a P2SH output. This is a best effort guess.
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

    /**
     * Decide whether an input spends a P2WPKH output. This is a best effort guess.
     */
    private static boolean isP2WpkhInput(List<ScriptChunk> chunks, TransactionWitness witness) {

        // P2WPKH inputs don't have any input script.

        if (chunks.size() > 0) {
            return false;
        }

        // P2WPKH input witnesses consist of a DER-encoded signature, followed by a public key.

        if (witness.getPushCount() != 2) {
            return false;
        }

        // A DER-encoded ECDSA signature (with a sig-hash type byte postfix) can measure at most 73
        // bytes, but might be as short as 9 bytes (~50% signatures are 72-bytes long).

        final byte[] signature = witness.getPush(0);
        if (signature == null || signature.length < 9 || signature.length > 73) {
            return false;
        }

        // A compressed public key measures 33 bytes and an uncompressed one 65 bytes.

        final byte[] publicKey = witness.getPush(1);
        if (publicKey == null || (publicKey.length != 33 && publicKey.length != 65)) {
            return false;
        }

        return true;
    }

    /**
     * Decide whether an input spends a P2WSH output. This is a best effort guess.
     */
    private static boolean isP2WshInput(List<ScriptChunk> chunks, TransactionWitness witness) {

        // P2WSH inputs don't have any input script.

        if (chunks.size() > 0) {
            return false;
        }

        // P2WSH input witnesses consist of a non-zero number of items, where the last one has the
        // witness script.

        if (witness.getPushCount() == 0) {
            return false;
        }

        final byte[] witnessScript = witness.getPush(witness.getPushCount() - 1);
        if (witnessScript == null) {
            return false;
        }

        return true;
    }

    /**
     * Extract all the recognizable addresses from the inputs and outputs of a transaction.
     */
    public static Set<String> getAddressesFromTransaction(Transaction transaction) {

        return getAddressesFromTransactions(Collections.singleton(transaction));
    }

    /**
     * Extract all the recognizable addresses from the inputs and outputs of a set of transactions.
     */
    public static Set<String> getAddressesFromTransactions(Collection<Transaction> transactions) {

        final Set<String> addresses = new HashSet<>();

        for (Transaction transaction : transactions) {

            for (TransactionInput input : transaction.getInputs()) {
                extractAddressFromInput(input).ifPresent(address ->
                        addresses.add(address.toString())
                );
            }

            for (TransactionOutput output : transaction.getOutputs()) {
                extractAddressFromOutput(output).ifPresent(address ->
                        addresses.add(address.toString())
                );
            }
        }

        return addresses;
    }
}
