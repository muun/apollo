package io.muun.common.crypto.schemes;

import io.muun.common.Supports;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;
import io.muun.common.utils.Since;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import javax.annotation.Nullable;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSEQUENCEVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIGVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_ENDIF;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;
import static org.bitcoinj.script.ScriptOpCodes.OP_IFDUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_NOTIF;

/**
 * Version 6 of the Muun transaction scheme.
 *
 * <p>Uses P2WSH for a channel funding that decays into a multi-sig 2-of-2 between the user key and
 * the cosigning key.
 */
@Since(
        apolloVersion = Supports.SubmarineSwapsV3.APOLLO,
        falconVersion = Supports.SubmarineSwapsV3.FALCON
)
public class TransactionSchemeV6 implements TransactionScheme {

    public static final int ADDRESS_VERSION = MuunAddress.VERSION_FUNDING_P2WSH;

    private static final int NUM_BLOCKS_FOR_EXPIRATION = 365 * 24 * 60 / 10; // 1 year

    TransactionSchemeV6() {
    }

    @Override
    public int getVersion() {
        return ADDRESS_VERSION;
    }

    @Override
    public boolean needsMuunSignature() {
        return false;
    }

    @Override
    public boolean needsSwapServerSignature() {
        return true;
    }

    /**
     * Create an address.
     */
    @Override
    public MuunAddress createAddress(PublicKeyTriple publicKeyTriple, NetworkParameters network) {

        final byte[] witnessScript = createWitnessScript(publicKeyTriple);
        final byte[] witnessScriptHash = Hashes.sha256(witnessScript);
        final String address = SegwitAddress.fromHash(network, witnessScriptHash).toBech32();

        return new MuunAddress(
                ADDRESS_VERSION,
                publicKeyTriple.getAbsoluteDerivationPath(),
                address
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
     * Create a witness for spending the channel funding output, if the user and the server are
     * willing to collaborate.
     */
    @Override
    public TransactionWitness createWitness(
            PublicKeyTriple publicKeyTriple,
            Signature userSignature,
            @Nullable Signature muunSignature,
            Signature swapServerSignature) {

        Preconditions.checkNotNull(userSignature);
        Preconditions.checkNotNull(swapServerSignature);

        final byte[] witnessScript = createWitnessScript(publicKeyTriple);

        final TransactionWitness witness = new TransactionWitness(3);
        witness.setPush(0, swapServerSignature.getBytes());
        witness.setPush(1, userSignature.getBytes());
        witness.setPush(2, witnessScript);
        return witness;
    }

    /**
     * Create the witness for spending the channel funding output, in case it expires.
     */
    public TransactionWitness createWitnessForUser(
            PublicKeyTriple publicKeyTriple,
            Signature userSignature,
            Signature muunSignature) {

        final byte[] witnessScript = createWitnessScript(publicKeyTriple);

        final TransactionWitness witness = new TransactionWitness(5);
        witness.setPush(0, muunSignature.getBytes());
        witness.setPush(1, publicKeyTriple.getMuunPublicKey().getPublicKeyBytes());
        witness.setPush(2, new byte[0]);
        witness.setPush(3, userSignature.getBytes());
        witness.setPush(4, witnessScript);
        return witness;
    }

    /**
     * Create the hash of a simplified form of a transaction, ready to be signed, for a specific
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

    /**
     * Create the witness script for spending the channel funding output.
     */
    private byte[] createWitnessScript(PublicKeyTriple publicKeyTriple) {

        final byte[] serverPublicKeyHash160 = Hashes.sha256Ripemd160(
                publicKeyTriple.getMuunPublicKey().getPublicKeyBytes()
        );

        // Equivalent miniscript (http://bitcoin.sipa.be/miniscript/):
        // and(
        //   pk(userPublicKey),
        //   or(
        //     10@pk(swapServerPublicKey),
        //     and(pk(muunPublicKey), older(NUM_BLOCKS_FOR_EXPIRATION))
        //   )
        // )

        return new ScriptBuilder()
                // Check whether the first stack item was a valid user signature
                .data(publicKeyTriple.getUserPublicKey().getPublicKeyBytes())
                .op(OP_CHECKSIGVERIFY)

                // Check whether the second stack item was a valid swap server signature
                .data(publicKeyTriple.getSwapServerPublicKey().getPublicKeyBytes())
                .op(OP_CHECKSIG)

                // If the swap server signature was NOT correct (if it was, leave a truthy value in
                // the stack)
                .op(OP_IFDUP)
                .op(OP_NOTIF)

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
                    // 20 bytes for the collaborative branch, which is the most frequent one.

                    // Validate that the fourth stack item was a valid server signature
                    .op(OP_CHECKSIGVERIFY)

                    // Validate that the blockchain height is big enough
                    .number(NUM_BLOCKS_FOR_EXPIRATION)
                    .op(OP_CHECKSEQUENCEVERIFY)

                .op(OP_ENDIF)
                .build()
                .getProgram();
    }
}
