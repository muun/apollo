package io.muun.common.crypto.hd;

import io.muun.common.bitcoinj.MainNetParamsY;
import io.muun.common.bitcoinj.MainNetParamsZ;
import io.muun.common.bitcoinj.NetworkParametersHelper;
import io.muun.common.bitcoinj.TestNetParamsU;
import io.muun.common.bitcoinj.TestNetParamsV;
import io.muun.common.crypto.hd.exception.InvalidDerivationBranchException;
import io.muun.common.crypto.hd.exception.InvalidDerivationPathException;
import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDDerivationException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class PrivateKey extends BaseKey {

    private static final int PRIVATE_KEY_LENGTH_IN_BYTES = 32;

    private static final int FINGERPRINT_LENGTH_IN_BYTES = 4;

    // TODO this should live in bitcoin lib (signTransactionHash could receive this as param).
    // If only we had default arguments...
    private static final int SIGHASH_ALL = 0x01;

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

        final DeterministicKey deterministicKey = wallet.getKeyByPath(new ArrayList<>());

        return new PrivateKey("m", deterministicKey, bitcoinContext.getParams());
    }

    public static PrivateKey fromMasterPrivateKey(
            @NotNull DeterministicKey masterKey,
            @NotNull NetworkParameters parameters
    ) {

        return new PrivateKey("m", masterKey, parameters);
    }

    /**
     * Deserialize a base58-encoded extended private key.
     */
    public static PrivateKey deserializeFromBase58(
            @NotNull String absoluteDerivationPath,
            @NotNull String base58Serialization
    ) {

        final NetworkParameters networkParameters = NetworkParametersHelper
                .getNetworkParametersForBase58Key(base58Serialization);

        final DeterministicKey deterministicKey = DeterministicKey.deserializeB58(
                base58Serialization,
                networkParameters
        );

        return new PrivateKey(
                absoluteDerivationPath,
                deterministicKey,
                networkParameters
        );
    }

    /**
     * Deserialize a private key from its compact byte representation.
     */
    public static PrivateKey fromCompactSerialization(
            @NotNull byte[] serialization,
            @NotNull NetworkParameters network
    ) {

        // the serialization for our extended private key is the concatenation of the raw private
        // key and the chain code, each of them 32 bytes
        final byte[] privateKey = ByteUtils.subArray(serialization, 0, PRIVATE_KEY_LENGTH_IN_BYTES);
        final byte[] chainCode = ByteUtils.subArray(serialization, PRIVATE_KEY_LENGTH_IN_BYTES);

        return PrivateKey.fromBytes32(privateKey, chainCode, network);
    }

    /**
     * Creates a private key from a byte32 & chainCode.
     */
    public static PrivateKey fromBytes32(
            @NotNull byte[] key32,
            @NotNull byte[] chainCode,
            NetworkParameters networkParameters
    ) {
        Preconditions.checkArgument(
                key32 != null && key32.length == 32,
                "key32 must be 32 bytes length"
        );
        Preconditions.checkArgument(
                chainCode != null && chainCode.length == 32,
                "chainCode must be 32 bytes length"
        );

        final DeterministicKey deterministicKey = new DeterministicKey(
                ImmutableList.of(),
                chainCode,
                Encodings.bytesToBigInteger(key32),
                null
        );

        return new PrivateKey("m", deterministicKey, networkParameters);
    }

    /**
     * Creates an extended private key from a DeterministicKey.
     */
    private PrivateKey(
            @NotNull String absoluteDerivationPath,
            @NotNull DeterministicKey deterministicKey,
            NetworkParameters networkParameters
    ) {

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
                throw new InvalidDerivationBranchException(
                        absoluteDerivationPath,
                        absolutePathToDerive
                );
            }

            final List<ChildNumber> childNumbersToDerive = childNumbers.subList(
                    parsedAbsoluteDerivationPath.size(),
                    childNumbers.size()
            );

            final DeterministicKey derivedKey = deriveDeterministicKey(
                    deterministicKey,
                    childNumbersToDerive
            );

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
     * Sign a given data with the private key. Use to sign challenge signature. Emulates
     * ChallengePrivateKey#verify().
     */
    @VisibleForTesting // For itests. Apollo uses Libwallet to do this.
    public byte[] sign(byte[] data) {
        final byte[] hash = Hashes.sha256(data);
        final ECKey.ECDSASignature signature = deterministicKey.sign(Sha256Hash.wrap(hash));

        return signature.encodeToDER();
    }

    /**
     * Compute the Bitcoin signature of a transaction hash.
     */
    public Signature signTransactionHash(byte[] txHash) {

        final byte[] derSignature = deterministicKey
                .sign(Sha256Hash.wrap(txHash))
                .encodeToDER();

        final byte[] sigData = ByteBuffer.allocate(derSignature.length + 1)
                .put(derSignature)
                .put((byte) SIGHASH_ALL)
                .array();

        return new Signature(sigData);
    }

    /**
     * Serialize to base 58.
     */
    public String serializeBase58() {
        return deterministicKey.serializePrivB58(networkParameters);
    }

    /**
     * Serialize the private key to a compact byte representation.
     */
    public byte[] toCompactSerialization() {

        // the plaintext for our extended private key is the concatenation of the raw private key
        // and the chain code, each of them 32 bytes
        return ByteUtils.concatenate(
                getPrivKey32(),
                deterministicKey.getChainCode()
        );
    }

    /**
     * Returns the ECPrivateKey of a deterministic key.
     */
    public ECPrivateKey getEcPrivateKey() {
        return Encodings.bytesToEcPrivateKey(getPrivKey32());
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
                return deriveFromRelativePath(nextIndex + "'");
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

    /**
     * Returns the BIP-32 fingerprint of this key (not the parent key).
     */
    public byte[] getFingerprint() {
        return Arrays.copyOfRange(deterministicKey.getIdentifier(), 0, FINGERPRINT_LENGTH_IN_BYTES);
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

        return networkParameters.equals(that.networkParameters)
                && Objects.equals(absoluteDerivationPath, that.absoluteDerivationPath)
                && Arrays.equals(deterministicKey.getChainCode(), thatKey.getChainCode())
                && Objects.equals(deterministicKey.getPrivKey(), thatKey.getPrivKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(absoluteDerivationPath, deterministicKey);
    }

    @Override
    public String toString() {
        return "PrivateKey{\n"
                + "\tabsoluteDerivationPath='" + absoluteDerivationPath + "',\n"
                + "\tparsedAbsoluteDerivationPath=" + parsedAbsoluteDerivationPath + ",\n"
                + "\tdeterministicKey=" + deterministicKey + "\n"
                + '}';
    }

    /**
     * Get the chain code.
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

    public boolean isXpriv() {
        return networkParameters.equals(MainNetParams.get());
    }

    public boolean isYpriv() {
        return networkParameters.equals(MainNetParamsY.get());
    }

    public boolean isZpriv() {
        return networkParameters.equals(MainNetParamsZ.get());
    }

    public boolean isTpriv() {
        return networkParameters.equals(TestNet3Params.get());
    }

    public boolean isUpriv() {
        return networkParameters.equals(TestNetParamsU.get());
    }

    public boolean isVpriv() {
        return networkParameters.equals(TestNetParamsV.get());
    }

    public boolean isFromMainnet() {
        return isXpriv() || isYpriv() || isZpriv();
    }

    public boolean isFromTestnet() {
        return isTpriv() || isUpriv() || isVpriv();
    }
}
