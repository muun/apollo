package io.muun.common.crypto.schemes;

import io.muun.common.Optional;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;

import com.google.common.collect.Maps;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

public interface TransactionScheme {

    TransactionScheme V1 = new TransactionSchemeV1();
    TransactionScheme V2 = new TransactionSchemeV2();
    TransactionScheme V3 = new TransactionSchemeV3();
    TransactionScheme V4 = new TransactionSchemeV4();
    TransactionScheme V6 = new TransactionSchemeV6();

    Map<Integer, TransactionScheme> ALL_SCHEMES = Maps.uniqueIndex(
            Arrays.asList(V1, V2, V3, V4, V6),
            TransactionScheme::getVersion
    );

    /**
     * Get the scheme matching a given version.
     */
    static Optional<TransactionScheme> get(int version) {
        return Optional.ofNullable(ALL_SCHEMES.get(version));
    }

    /**
     * Get all existing schemes.
     */
    static Collection<TransactionScheme> getAll() {
        return ALL_SCHEMES.values();
    }

    /**
     * Get the version of this scheme.
     */
    int getVersion();

    /**
     * Check whether this scheme needs a muun signature.
     */
    default boolean needsMuunSignature() {
        return true;
    }

    /**
     * Check whether this scheme needs a swap server signature.
     */
    default boolean needsSwapServerSignature() {
        return false;
    }

    /**
     * Create an address.
     */
    MuunAddress createAddress(PublicKeyTriple publicKeyTriple, NetworkParameters network);

    /**
     * Create the input script given signatures of the TX.
     */
    Script createInputScript(
            PublicKeyTriple publicKeyTriple,
            @Nullable Signature userSignature,
            @Nullable Signature muunSignature,
            @Nullable Signature swapServerSignature);

    /**
     * Create the script to use when sending to an address of this scheme.
     */
    Script createOutputScript(MuunAddress address);

    /**
     * Create the script to use for an output with this scheme given the pub keys.
     */
    default Script createOutputScript(PublicKeyTriple publicKeyTriple) {
        // the network doesn't matter, since it will be ignored
        final MuunAddress address = createAddress(publicKeyTriple, MainNetParams.get());
        return createOutputScript(address);
    }

    /**
     * Create the witness script and stack for segwit spends.
     */
    TransactionWitness createWitness(
            PublicKeyTriple publicKeyTriple,
            @Nullable Signature userSignature,
            @Nullable Signature muunSignature,
            @Nullable Signature swapServerSignature);

    /**
     * Create the digest to sign for a spend.
     */
    byte[] createDataToSignInput(
            Transaction transaction,
            int inputIndex,
            long amountInSat,
            PublicKeyTriple publicKeyTriple);
}
