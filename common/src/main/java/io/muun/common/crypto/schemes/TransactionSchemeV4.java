package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKMULTISIG;

/**
 * Version 4 of the Muun transaction scheme.
 * Uses standard Pay-to-Witness-Script-Hash multisig 2-of-2 scripts for
 * inputs and outputs. The first key belongs to the User receiving money, the second to Houston.
 */
public class TransactionSchemeV4 {

    public static final int ADDRESS_VERSION = MuunAddress.VERSION_COSIGNED_P2WSH;

    /**
     * Create an address.
     */
    public static MuunAddress createAddress(PublicKeyPair publicKeyPair, NetworkParameters params) {
        final Script witnessScript = createWitnessScript(publicKeyPair);
        final byte[] witnessScriptHash = Sha256Hash.hash(witnessScript.getProgram());
        final SegwitAddress address = SegwitAddress.fromHash(params, witnessScriptHash);

        return new MuunAddress(
                ADDRESS_VERSION,
                publicKeyPair.getAbsoluteDerivationPath(),
                address.toBech32()
        );
    }

    /**
     * Create the (empty) input script.
     */
    public static Script createInputScript(PublicKeyPair publicKeyPair) {
        return ScriptBuilder.createEmpty();
    }

    /**
     * Create an output script, given the user address.
     */
    public static Script createOutputScript(MuunAddress userAddress) {
        return createOutputScript(userAddress.getHash());
    }

    private static Script createOutputScript(byte[] scriptHash256) {
        return new ScriptBuilder()
                .smallNum(0)
                .data(scriptHash256)
                .build();
    }

    /**
     * Create the hash of a simplified form of a Transaction, ready to be signed, for a specific
     * input index.
     */
    public static byte[] createDataToSignInput(Transaction transaction,
                                               int inputIndex,
                                               long amount,
                                               PublicKeyPair publicKeyPair) {

        return TransactionHelpers.getSegwitDataToSign(
                transaction,
                inputIndex,
                createWitnessScript(publicKeyPair),
                Coin.valueOf(amount)
        );
    }

    /**
     * Create the witness structure, according to our signing scheme.
     * Namely, Pay-to-Script-Hash-of-Pay-to-Witness-Script-Hash multisig 2-of-2.
     */
    public static TransactionWitness createWitness(PublicKeyPair publicKeyPair,
                                                   Signature userSignature,
                                                   Signature cosignerSignature) {

        final Script witnessScript = createWitnessScript(publicKeyPair);

        // This correlates to TransactionSchemeV2 inputScript
        final TransactionWitness witness = new TransactionWitness(4);
        witness.setPush(0, new byte[0]);
        witness.setPush(1, userSignature.getBytes());
        witness.setPush(2, cosignerSignature.getBytes());
        witness.setPush(3, witnessScript.getProgram());

        return witness;
    }

    private static Script createWitnessScript(PublicKeyPair publicKeyPair) {
        // This correlates to TransactionSchemeV2 redeemScript
        return new ScriptBuilder()
                .smallNum(2) // required signatures
                .data(publicKeyPair.getUserPublicKey().getPublicKeyBytes())
                .data(publicKeyPair.getMuunPublicKey().getPublicKeyBytes())
                .smallNum(2) // total signatures
                .op(OP_CHECKMULTISIG)
                .build();
    }
}

