package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import javax.annotation.Nullable;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKMULTISIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUAL;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;

/**
 * Version 3 of the Muun transaction scheme.
 * Uses standard Pay-to-Script-Hash-of-Pay-to-Witness-Script-Hash multisig 2-of-2 scripts for
 * inputs and outputs. The first key belongs to the User receiving money, the second to Houston.
 */
public class TransactionSchemeV3 implements TransactionScheme {

    public static final int ADDRESS_VERSION = MuunAddress.VERSION_COSIGNED_P2SH_P2WSH;

    TransactionSchemeV3() {
    }

    @Override
    public int getVersion() {
        return ADDRESS_VERSION;
    }

    /**
     * Create an address.
     */
    @Override
    public MuunAddress createAddress(PublicKeyTriple publicKeyTriple, NetworkParameters params) {

        final byte[] redeemScript = createRedeemScript(publicKeyTriple);
        final byte[] addressHash160 = Hashes.sha256Ripemd160(redeemScript);

        return new MuunAddress(
                ADDRESS_VERSION,
                publicKeyTriple.getAbsoluteDerivationPath(),
                LegacyAddress.fromScriptHash(params, addressHash160).toString()
        );
    }

    /**
     * Create an input script.
     */
    @Override
    public Script createInputScript(
            PublicKeyTriple publicKeyTriple,
            @Nullable Signature userSignature,
            @Nullable Signature muunSignature,
            @Nullable Signature swapServerSignature) {

        final byte[] redeemScript = createRedeemScript(publicKeyTriple);

        return new ScriptBuilder()
                .data(redeemScript)
                .build();
    }

    /**
     * Create an output script.
     */
    @Override
    public Script createOutputScript(MuunAddress address) {

        final byte[] redeemScriptHash160 = address.getHash();

        return new ScriptBuilder()
                .op(OP_HASH160)
                .data(redeemScriptHash160)
                .op(OP_EQUAL)
                .build();
    }

    /**
     * Create a witness.
     */
    @Override
    public TransactionWitness createWitness(
            PublicKeyTriple publicKeyTriple,
            Signature userSignature,
            Signature muunSignature,
            @Nullable Signature swapServerSignature) {

        Preconditions.checkNotNull(userSignature);
        Preconditions.checkNotNull(muunSignature);

        final byte[] witnessScript = createWitnessScript(publicKeyTriple);

        final TransactionWitness witness = new TransactionWitness(4);
        witness.setPush(0, new byte[0]);
        witness.setPush(1, userSignature.getBytes());
        witness.setPush(2, muunSignature.getBytes());
        witness.setPush(3, witnessScript);

        return witness;
    }

    /**
     * Create the hash of a simplified form of a Transaction, ready to be signed, for a specific
     * input index.
     */
    @Override
    public byte[] createDataToSignInput(
            Transaction transaction,
            int inputIndex,
            long amountInSat,
            PublicKeyTriple publicKeyTriple) {

        final byte[] witnessScript = createWitnessScript(publicKeyTriple);

        return TransactionHelpers.getSegwitDataToSign(
                transaction,
                inputIndex,
                new Script(witnessScript),
                Coin.valueOf(amountInSat)
        );
    }

    private byte[] createRedeemScript(PublicKeyTriple publicKeyTriple) {

        final byte[] witnessScript = createWitnessScript(publicKeyTriple);

        // Always 32 bytes
        final byte[] witnessScriptHash = Hashes.sha256(witnessScript);

        // Always 34 bytes: push 0 + push 32-bytes + 32 bytes
        return new ScriptBuilder()
                .smallNum(0)
                .data(witnessScriptHash)
                .build()
                .getProgram();
    }

    private byte[] createWitnessScript(PublicKeyTriple publicKeyTriple) {

        return new ScriptBuilder()
                .smallNum(2) // required signatures
                .data(publicKeyTriple.getUserPublicKey().getPublicKeyBytes())
                .data(publicKeyTriple.getMuunPublicKey().getPublicKeyBytes())
                .smallNum(2) // total signatures
                .op(OP_CHECKMULTISIG)
                .build()
                .getProgram();
    }
}
