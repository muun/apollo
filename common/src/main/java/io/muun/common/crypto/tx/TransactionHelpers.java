package io.muun.common.crypto.tx;

import io.muun.common.Optional;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptException;

import java.util.List;

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
        return tx.hashForSignatureWitness(inputIndex, witnessScript, value, SigHash.ALL, false)
                .getBytes();
    }

    /**
     * Compute a transaction hash that is independent of malleability (ie. malleated transactions
     * get the same hash value).
     *
     * <p>We build the transaction with the same inputs and outputs, but without the input scripts,
     * and return the hash of this transaction.
     */
    public static String getNonMalleableHash(org.bitcoinj.core.Transaction tx) {

        final org.bitcoinj.core.Transaction txClone = new org.bitcoinj.core.Transaction(
                tx.getParams(),
                tx.unsafeBitcoinSerialize()
        );

        for (TransactionInput input : txClone.getInputs()) {
            input.setScriptSig(new Script(new byte[]{}));
        }

        return txClone.getHashAsString();
    }

    /**
     * Get the hex-encoded raw transaction.
     */
    public static String getHexRawTransaction(org.bitcoinj.core.Transaction tx) {

        return Encodings.bytesToHex(tx.unsafeBitcoinSerialize());
    }

    /**
     * Get the destination address from a given output.
     */
    public static String getAddressFromOutput(TransactionOutput output,
                                              NetworkParameters networkParameters) {
        try {
            return output.getScriptPubKey().getToAddress(networkParameters).toString();
        } catch (ScriptException exception) {
            return null; // Bitcoinj sometimes fail to parse non-standard outputs
        }
    }

    /**
     * Get the spent address from a given P2PKH input.
     */
    public static String getAddressFromInput(TransactionInput input,
                                             NetworkParameters networkParameters) {
        final Address address = extractAddressFromInput(input).orElse(null);
        return address != null ? address.toBase58() : null;
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

        final Script script = input.getScriptSig();
        final List<ScriptChunk> chunks = script.getChunks();
        final NetworkParameters network = input.getParams();

        if (isP2Pkh(chunks)) {
            final byte[] rawPubKey = chunks.get(1).data;
            final byte[] addressHash = Hashes.sha256Ripemd160(rawPubKey);

            final int p2PkhHeader = network.getAddressHeader();
            return Optional.of(new Address(network, p2PkhHeader, addressHash));
        }

        if (isP2Sh(chunks)) {
            final byte[] rawRedeemScript = chunks.get(chunks.size() - 1).data;
            final byte[] addressHash = Hashes.sha256Ripemd160(rawRedeemScript);

            final int p2ShHeader = network.getP2SHHeader();
            return Optional.of(new Address(network, p2ShHeader, addressHash));
        }

        return Optional.empty();
    }

    /**
     * Decide whether a script is a P2PKH-spending input script. This is a best effort guess.
     */
    private static boolean isP2Pkh(List<ScriptChunk> chunks) {

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
    private static boolean isP2Sh(List<ScriptChunk> chunks) {

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
