package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;

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
 * Version 2 of the Muun transaction scheme.
 * Uses standard Pay-to-Script-Hash multisig 2-of-2 scripts for inputs and outputs. The first key
 * belongs to the User receiving money, the second to Houston.
 */
public class TransactionSchemeV2 implements TransactionScheme {

    public static final int ADDRESS_VERSION = MuunAddress.VERSION_COSIGNED_P2SH;

    TransactionSchemeV2() {
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
            Signature userSignature,
            Signature muunSignature,
            @Nullable Signature swapServerSignature) {

        Preconditions.checkNotNull(userSignature);
        Preconditions.checkNotNull(muunSignature);

        final byte[] redeemScript = createRedeemScript(publicKeyTriple);

        return new ScriptBuilder()
                .smallNum(0) // if this OP_0 confuses you, ask somebody. Funny stories guaranteed.
                .data(userSignature.getBytes())
                .data(muunSignature.getBytes())
                .data(redeemScript)
                .build();
    }

    /**
     * Create an output script.
     */
    @Override
    public Script createOutputScript(MuunAddress address) {

        return new ScriptBuilder()
                .op(OP_HASH160)
                .data(address.getHash())
                .op(OP_EQUAL)
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

        final byte[] redeemScript = createRedeemScript(publicKeyTriple);

        return TransactionHelpers.getDataToSign(
                transaction,
                inputIndex,
                new Script(redeemScript)
        );
    }

    private byte[] createRedeemScript(PublicKeyTriple publicKeyTriple) {

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
