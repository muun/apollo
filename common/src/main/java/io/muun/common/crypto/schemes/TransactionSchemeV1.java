package io.muun.common.crypto.schemes;


import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import javax.annotation.Nullable;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;

/**
 * Version 1 of the Muun transaction scheme.
 * Uses standard Pay-to-Public-Key-Hash scripts for inputs and outputs.
 */
public class TransactionSchemeV1 implements TransactionScheme {

    public static final int ADDRESS_VERSION = MuunAddress.VERSION_P2PKH;

    TransactionSchemeV1() {
    }

    @Override
    public int getVersion() {
        return ADDRESS_VERSION;
    }

    @Override
    public boolean needsMuunSignature() {
        return false;
    }

    /**
     * Create an address.
     */
    @Override
    public MuunAddress createAddress(PublicKeyTriple publicKeyTriple, NetworkParameters network) {

        return new MuunAddress(
                ADDRESS_VERSION,
                publicKeyTriple.getAbsoluteDerivationPath(),
                publicKeyTriple.getUserPublicKey().toAddress()
        );
    }

    /**
     * Create an input script.
     */
    @Override
    public Script createInputScript(
            PublicKeyTriple publicKeyTriple,
            Signature userSignature,
            @Nullable Signature muunSignature,
            @Nullable Signature swapServerSignature) {

        Preconditions.checkNotNull(userSignature);

        return new ScriptBuilder()
                .data(userSignature.getBytes())
                .data(publicKeyTriple.getUserPublicKey().getPublicKeyBytes())
                .build();
    }

    /**
     * Create an output script.
     */
    @Override
    public Script createOutputScript(MuunAddress address) {

        return new ScriptBuilder()
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(address.getHash())
                .op(OP_EQUALVERIFY)
                .op(OP_CHECKSIG)
                .build();
    }

    /**
     * Create a witness.
     */
    @Override
    public TransactionWitness createWitness(
            PublicKeyTriple publicKeyTriple,
            @Nullable Signature userSignature,
            @Nullable Signature muunSignature,
            @Nullable Signature swapServerSignature) {

        return TransactionWitness.EMPTY;
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

        return TransactionHelpers.getDataToSign(
                transaction,
                inputIndex,
                createOutputScript(publicKeyTriple)
        );
    }
}
