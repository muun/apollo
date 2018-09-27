package io.muun.common.crypto.hd;

import io.muun.common.bitcoinj.NetworkParametersHelper;
import io.muun.common.crypto.hd.exception.InvalidDerivationBranchException;
import io.muun.common.crypto.hd.exception.InvalidDerivationPathException;
import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Encodings;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDDerivationException;
import org.bitcoinj.wallet.Wallet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.validation.constraints.NotNull;

public class PrivateKey extends BaseKey {

    @NotNull
    private final String absoluteDerivationPath;

    @NotNull
    private final List<ChildNumber> parsedAbsoluteDerivationPath;

    @NotNull
    private final DeterministicKey deterministicKey;

    @NotNull
    private final NetworkParameters networkParameters;

    /**
     * Generates a new root private key.
     */
    public static PrivateKey getNewRootPrivateKey(@NotNull NetworkParameters networkParameters) {
        return getNewRootPrivateKey(Context.getOrCreate(networkParameters));
    }

    /**
     * Generates a new root private key, using a custom BitcoinJ Context.
     * WARNING: will not validate that the default network is being used (testnet/mainnet). Use
     * the variant that takes NetworkParameters in application code.
     */
    @VisibleForTesting
    public static PrivateKey getNewRootPrivateKey(@NotNull Context bitcoinContext) {
        final Wallet wallet = new Wallet(bitcoinContext);

        final DeterministicKey deterministicKey = wallet.getKeyByPath(
                new ArrayList<org.bitcoinj.crypto.ChildNumber>());

        return new PrivateKey("m", deterministicKey, bitcoinContext.getParams());
    }

    /**
     * Deserialize a base58-encoded extended private key.
     */
    public static PrivateKey deserializeFromBase58(@NotNull String absoluteDerivationPath,
                                                   @NotNull String base58Serialization) {

        final NetworkParameters networkParameters = NetworkParametersHelper
                .getNetworkParametersForBase58Key(base58Serialization);

        final DeterministicKey deterministicKey = DeterministicKey.deserializeB58(
                base58Serialization,
                networkParameters
        );

        return new PrivateKey(
                absoluteDerivationPath,
                deterministicKey,
                networkParameters);
    }

    /**
     * Creates an extended private key from a DeterministicKey.
     */
    private PrivateKey(@NotNull String absoluteDerivationPath,
                       @NotNull DeterministicKey deterministicKey,
                       NetworkParameters networkParameters) {

        if (deterministicKey.isPubKeyOnly()) {
            throw new IllegalArgumentException("No private key provided.");
        }

        this.deterministicKey = deterministicKey;
        this.parsedAbsoluteDerivationPath = DerivationPathUtils.parsePath(absoluteDerivationPath);
        this.absoluteDerivationPath = absoluteDerivationPath;
        this.networkParameters = networkParameters;
    }

    /**
     * Returns this key's extended public key.
     */
    public PublicKey getPublicKey() {
        // NOTE: we DO NOT USE `.dropPrivateBytes()` because (hang on to your chair) BitcoinJ
        // sometimes discards the depth information from the key in the process.

        // How does this happen?
        // When the public-only key is constructed, the depth is not kept but rather re-calculated
        // from (parent.depth + 1). However, if the key was deserialized instead of constructed
        // locally, parent will be null, and depth will reset to 0.
        final DeterministicKey publicKey = DeterministicKey.deserializeB58(
                deterministicKey.serializePubB58(networkParameters),
                networkParameters
        );

        return new PublicKey(absoluteDerivationPath, publicKey, networkParameters);
    }

    @NotNull
    public String getAbsoluteDerivationPath() {
        return absoluteDerivationPath;
    }

    /**
     * Return a PrivateKey with the same key material, but without derivation path information.
     */
    public PrivateKey asRootPrivateKey() {
        final ImmutableList<org.bitcoinj.crypto.ChildNumber> emptyPath = ImmutableList.of();

        final DeterministicKey rootDeterministicKey = new DeterministicKey(
                emptyPath,
                deterministicKey.getChainCode(),
                deterministicKey.getPubKeyPoint(),
                deterministicKey.getPrivKey(),
                null
        );

        return new PrivateKey("m", rootDeterministicKey, networkParameters);
    }

    /**
     * Derives a private key from its absolute derivation path.
     *
     * @throws KeyDerivationException           If the derivation fails (see BIP32).
     * @throws InvalidDerivationBranchException If the path doesn't belong to the current derivation
     *                                          branch.
     * @throws InvalidDerivationPathException   If the path is invalid.
     */
    public PrivateKey deriveFromAbsolutePath(@NotNull String absolutePathToDerive)
            throws KeyDerivationException, InvalidDerivationBranchException,
            InvalidDerivationPathException {

        try {

            final List<ChildNumber> childNumbers = DerivationPathUtils.parsePath(
                    absolutePathToDerive);

            if (!isPrefix(parsedAbsoluteDerivationPath, childNumbers)) {
                throw new InvalidDerivationBranchException(absoluteDerivationPath,
                        absolutePathToDerive);
            }

            final List<ChildNumber> childNumbersToDerive = childNumbers.subList(
                    parsedAbsoluteDerivationPath.size(),
                    childNumbers.size()
            );

            final DeterministicKey derivedKey = deriveDeterministicKey(deterministicKey,
                    childNumbersToDerive);

            return new PrivateKey(absolutePathToDerive, derivedKey, networkParameters);

        } catch (HDDerivationException exception) {
            throw new KeyDerivationException(exception);
        }
    }

    /**
     * Derives a private key from a relative derivation path starting at this key.
     *
     * @throws KeyDerivationException           If the derivation fails (see BIP32).
     * @throws InvalidDerivationBranchException If the path doesn't belong to the current derivation
     *                                          branch.
     * @throws InvalidDerivationPathException   If the path is invalid.
     */
    public PrivateKey deriveFromRelativePath(@NotNull String relativePathToDerive)
            throws KeyDerivationException, InvalidDerivationBranchException,
            InvalidDerivationPathException {

        return deriveFromAbsolutePath(absoluteDerivationPath + "/" + relativePathToDerive);
    }

    /**
     * Compute the Bitcoin signature of a transaction hash.
     */
    public Signature signTransactionHash(byte[] txHash) {
        final int hashType = TransactionHelpers.SIGHASH_ALL;

        final byte[] derSignature = deterministicKey
                .sign(Sha256Hash.wrap(txHash))
                .encodeToDER();

        final byte[] sigData = ByteBuffer.allocate(derSignature.length + 1)
                .put(derSignature)
                .put((byte) hashType)
                .array();

        return new Signature(sigData);
    }

    public String serializeBase58() {
        return deterministicKey.serializePrivB58(networkParameters);
    }

    /**
     * Derives the next possible child starting at {nextIndex}.
     */
    public PrivateKey deriveNextValidChild(int nextIndex) {

        while (true) {

            try {
                return deriveFromRelativePath(String.valueOf(nextIndex));
            } catch (KeyDerivationException e) {
                nextIndex += 1;
            }
        }

    }

    /**
     * Derives the next possible hardened child starting at {nextIndex}.
     */
    public PrivateKey deriveNextValidHardenedChild(int nextIndex) {

        while (true) {

            try {
                return deriveFromRelativePath(String.valueOf(nextIndex) + "'");
            } catch (KeyDerivationException e) {
                nextIndex += 1;
            }
        }

    }

    public int getLastLevelIndex() {
        return deterministicKey.getChildNumber().num();
    }

    public DeterministicKey getDeterministicKey() {
        return deterministicKey;
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof PrivateKey)) {
            return false;
        }

        final PrivateKey that = (PrivateKey) other;
        final DeterministicKey thatKey = that.deterministicKey;

        return Objects.equals(absoluteDerivationPath, that.absoluteDerivationPath)
                && Arrays.equals(deterministicKey.getChainCode(), thatKey.getChainCode())
                && Objects.equals(deterministicKey.getPath(), thatKey.getPath())
                && Objects.equals(deterministicKey.getPrivKey(), thatKey.getPrivKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(absoluteDerivationPath, deterministicKey);
    }

    @Override
    public String toString() {
        return "PrivateKey{\n"
                + "\tabsoluteDerivationPath='" + absoluteDerivationPath + "\',\n"
                + "\tparsedAbsoluteDerivationPath=" + parsedAbsoluteDerivationPath + ",\n"
                + "\tdeterministicKey=" + deterministicKey + "\n"
                + '}';
    }

    /**
     * @return chain code from a `DeterministicKey`.
     */
    public byte[] getChainCode() {
        return deterministicKey.getChainCode();
    }

    /**
     * Returns the private key of a deterministic key.
     */
    public byte[] getPrivKey32() {
        return Encodings.bigIntegerToBytes(deterministicKey.getPrivKey(), 32);
    }

}
