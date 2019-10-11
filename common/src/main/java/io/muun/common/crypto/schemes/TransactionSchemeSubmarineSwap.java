package io.muun.common.crypto.schemes;

import io.muun.common.api.MuunAddressJson;
import io.muun.common.api.SubmarineSwapJson;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.LnInvoice;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_DROP;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_ELSE;
import static org.bitcoinj.script.ScriptOpCodes.OP_ENDIF;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUAL;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;
import static org.bitcoinj.script.ScriptOpCodes.OP_IF;

/**
 * A TransactionScheme to generate SubmarineSwap scripts, refund addresses and spending inputs.
 */
public class TransactionSchemeSubmarineSwap {

    public static final int CLIENT_VERSION = 35;
    public static final int ADDRESS_VERSION = MuunAddress.VERSION_SUBMARINE_SWAP_REFUND;

    /**
     * Create a refund address for the failure case of the SubmarineSwap.
     */
    public static MuunAddress createRefundAddress(PublicKey refundPublicKey) {

        return new MuunAddress(
                ADDRESS_VERSION,
                refundPublicKey.getAbsoluteDerivationPath(),
                refundPublicKey.toAddress()
        );
    }

    /**
     * Create an address.
     */
    public static Address createAddress(NetworkParameters network, byte[] witnessScript) {

        final Script redeemScript = createRedeemScript(witnessScript);
        final byte[] addressHash160 = getScriptHash(redeemScript);

        return LegacyAddress.fromScriptHash(network, addressHash160);
    }

    /**
     * Create the output script.
     */
    public static Script createOutputScript(byte[] witnessScript) {

        final Script redeemScript = createRedeemScript(witnessScript);

        return new ScriptBuilder()
                .op(OP_HASH160)
                .data(getScriptHash(redeemScript))
                .op(OP_EQUAL)
                .build();
    }

    private static byte[] getScriptHash(Script redeemScript) {

        return Utils.sha256hash160(redeemScript.getProgram());
    }

    /**
     * Create a SubmarineSwap input script.
     */
    public static Script createInputScript(byte[] witnessScript) {

        final Script redeemScript = createRedeemScript(witnessScript);

        return new ScriptBuilder()
                .data(redeemScript.getProgram())
                .build();
    }

    private static Script createRedeemScript(byte[] witnessScript) {

        // Always 32 bytes
        final byte[] witnessScriptHash = Sha256Hash.hash(witnessScript);

        // Always 34 bytes: push 0 + push 32-bytes + 32 bytes
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
     * Create the witness for spending the submarine swap in case the swap expires.
     */
    public static TransactionWitness createWitnessForUser(PublicKey userPublicKey,
                                                          Signature userSignature,
                                                          byte[] witnessScript) {

        final TransactionWitness witness = new TransactionWitness(3);
        witness.setPush(0, userSignature.getBytes());
        witness.setPush(1, userPublicKey.getPublicKeyBytes());
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
        witness.setPush(0, swapServerSignature.getBytes());
        witness.setPush(1, swapPaymentSecret);
        witness.setPush(2, witnessScript);

        return witness;
    }

    /**
     * Create the witness script for spending the submarine output.
     */
    public static byte[] createWitnessScript(String refundAddress,
                                             byte[] swapPaymentHash256,
                                             byte[] swapServerPublicKey,
                                             long lockTime) {

        // It turns out that the payment hash present in an invoice is just the SHA256 of the
        // payment preimage, so we still have to do a pass of RIPEMD160 before pushing it to the
        // script
        final byte[] swapPaymentHash160 = Hashes.ripemd160(swapPaymentHash256);

        final byte[] refundPublicKeyHash160 = LegacyAddress.fromString(null, refundAddress)
                .getHash();

        return new ScriptBuilder()
                .op(OP_DUP)

                // Condition to decide which branch to follow:
                .op(OP_HASH160)
                .data(swapPaymentHash160)
                .op(OP_EQUAL)

                // SubmarineSwap service spending script, for successful LN payments:
                .op(OP_IF)
                .op(OP_DROP)
                .data(swapServerPublicKey)

                // User spending script, for failed LN payments:
                .op(OP_ELSE)
                .number(lockTime)
                .op(OP_CHECKLOCKTIMEVERIFY)
                .op(OP_DROP)
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(refundPublicKeyHash160)
                .op(OP_EQUALVERIFY)

                // Final verification for both branches:
                .op(OP_ENDIF)
                .op(OP_CHECKSIG)
                .build()
                .getProgram();
    }

    /**
     * Validate Submarine Swap Server response. The end goal is to verify that the redeem script
     * returned by the server is the script that is actually encoded in the reported swap address.
     * NOTE: this method does not ask the refundAddress for network parameters and instead receives
     * them as a parameter because the base58 serialization of bitcoin addresses does not
     * differentiate between testnet and regtest (and so this information is lost and cause trouble
     * on local environment).
     */
    public static boolean validateSwap(String invoice,
                                       PublicKeyPair userPublicKeyPair,
                                       SubmarineSwapJson swapJson,
                                       NetworkParameters network) {

        // parse invoice
        final LnInvoice decoded = LnInvoice.decode(network, invoice);

        // Check that the payment hash matches the invoice
        final String paymentHashInHex = swapJson.fundingOutput.serverPaymentHashInHex;
        if (!decoded.id.equals(paymentHashInHex)) {
            return false;
        }

        //TODO: check that timelock is acceptable

        // Check that the refund address belongs to the user
        final MuunAddressJson swapRefundAddress = swapJson.fundingOutput.userRefundAddress;
        final PublicKeyPair derivedPublicKeyPair = userPublicKeyPair
                .deriveFromAbsolutePath(swapRefundAddress.derivationPath);

        final MuunAddress derivedAddress = MuunAddress.create(
                swapRefundAddress.version,
                derivedPublicKeyPair
        );

        if (!derivedAddress.getAddress().equals(swapRefundAddress.address)) {
            return false;
        }

        // Check that the witness script was computed according to the given parameters
        final byte[] witnessScript = createWitnessScript(
                swapRefundAddress.address,
                Encodings.hexToBytes(paymentHashInHex),
                Encodings.hexToBytes(swapJson.fundingOutput.serverPublicKeyInHex),
                swapJson.fundingOutput.userLockTime
        );

        // Check that the script hashes to the output address we'll be using
        final Address outputAddress = createAddress(network, witnessScript);

        if (!outputAddress.toString().equals(swapJson.fundingOutput.outputAddress)) {
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
