package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import javax.annotation.Nullable;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKMULTISIG;

/**
 * Version 4 of the Muun transaction scheme.
 * Uses standard Pay-to-Witness-Script-Hash multisig 2-of-2 scripts for
 * inputs and outputs. The first key belongs to the User receiving money, the second to Houston.
 */
public class TransactionSchemeV4 implements TransactionScheme {

    public static final int ADDRESS_VERSION = MuunAddress.VERSION_COSIGNED_P2WSH;

    TransactionSchemeV4() {
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

        final byte[] witnessScript = createWitnessScript(publicKeyTriple);
        final byte[] witnessScriptHash = Hashes.sha256(witnessScript);
        final SegwitAddress address = SegwitAddress.fromHash(params, witnessScriptHash);

        return new MuunAddress(
                ADDRESS_VERSION,
                publicKeyTriple.getAbsoluteDerivationPath(),
                address.toBech32()
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

        return ScriptBuilder.createEmpty();
    }

    /**
     * Create an output script.
     */
    @Override
    public Script createOutputScript(MuunAddress address) {

        final byte[] scriptHash256 = address.getHash();

        return new ScriptBuilder()
                .smallNum(0)
                .data(scriptHash256)
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
