package io.muun.common.crypto.hd;

import io.muun.common.bitcoinj.NetworkParametersHelper;
import io.muun.common.crypto.hd.exception.InvalidDerivationBranchException;
import io.muun.common.crypto.hd.exception.InvalidDerivationPathException;
import io.muun.common.crypto.hd.exception.KeyDerivationException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDDerivationException;

import java.util.List;

import javax.validation.constraints.NotNull;

public class PublicKey extends BaseKey {

    @NotNull
    private final String absoluteDerivationPath;

    @NotNull
    private final List<ChildNumber> parsedAbsoluteDerivationPath;

    @NotNull
    private final DeterministicKey deterministicKey;

    @NotNull
    private final NetworkParameters networkParameters;

    /**
     * Deserialize a base58-encoded extended public key.
     */
    public static PublicKey deserializeFromBase58(@NotNull String absoluteDerivationPath,
                                                  @NotNull String base58Serialization) {

        final NetworkParameters networkParameters =
                NetworkParametersHelper.getNetworkParametersForBase58Key(base58Serialization);

        final DeterministicKey deterministicKey = DeterministicKey.deserializeB58(
                base58Serialization,
                networkParameters
        );

        return new PublicKey(
                absoluteDerivationPath,
                deterministicKey,
                networkParameters
        );
    }

    /**
     * Creates an extended public key.
     */
    PublicKey(
            @NotNull String absoluteDerivationPath,
            @NotNull DeterministicKey deterministicKey,
            @NotNull NetworkParameters networkParameters) {

        this.absoluteDerivationPath = absoluteDerivationPath;
        this.parsedAbsoluteDerivationPath = DerivationPathUtils.parsePath(absoluteDerivationPath);
        this.deterministicKey = deterministicKey;
        this.networkParameters = networkParameters;
    }

    /**
     * Derives a public key from its absolute derivation path.
     *
     * @throws KeyDerivationException           If the derivation fails (see BIP32).
     * @throws InvalidDerivationBranchException If the path doesn't belong to the current derivation
     *                                          branch.
     * @throws InvalidDerivationPathException   If the path is invalid.
     */
    public PublicKey deriveFromAbsolutePath(@NotNull String absolutePathToDerive)
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

            if (anyHardened(childNumbersToDerive)) {
                throw new IllegalArgumentException(
                        "Trying to derive a hardened child from a public key");
            }

            final DeterministicKey derivedKey = deriveDeterministicKey(deterministicKey,
                    childNumbersToDerive);

            return new PublicKey(absolutePathToDerive, derivedKey, networkParameters);

        } catch (HDDerivationException exception) {
            throw new KeyDerivationException(exception);
        }
    }

    /**
     * Derives a public key from a relative derivation path starting at this key.
     *
     * @throws KeyDerivationException           If the derivation fails (see BIP32).
     * @throws InvalidDerivationBranchException If the path doesn't belong to the current derivation
     *                                          branch.
     * @throws InvalidDerivationPathException   If the path is invalid.
     */
    public PublicKey deriveFromRelativePath(@NotNull String relativePathToDerive)
            throws KeyDerivationException, InvalidDerivationBranchException,
            InvalidDerivationPathException {
        return deriveFromAbsolutePath(absoluteDerivationPath + "/" + relativePathToDerive);
    }

    /**
     * Returns the P2PKH address of this key.
     */
    public String toAddress() {
        final Address address = new Address(networkParameters, deterministicKey.getPubKeyHash());
        return address.toString();
    }

    public String serializeBase58() {
        return deterministicKey.serializePubB58(networkParameters);
    }

    public byte[] getPublicKeyBytes() {
        return deterministicKey.getPubKey();
    }

    public boolean verifyTransactionHash(byte[] txHash, Signature signature) {
        return deterministicKey.verify(txHash, signature.getBytes());
    }

    @NotNull
    public NetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    @NotNull
    public String getAbsoluteDerivationPath() {
        return absoluteDerivationPath;
    }

    /**
     * Derives the next possible child starting at {nextIndex}, inclusive.
     */
    public PublicKey deriveNextValidChild(int nextIndex) {
        while (true) {
            try {
                return deriveFromRelativePath(String.valueOf(nextIndex));
            } catch (KeyDerivationException e) {
                nextIndex += 1;
            }
        }

    }

    public PublicKey deriveChild(int childIndex) throws KeyDerivationException {
        return deriveFromRelativePath(String.valueOf(childIndex));
    }

    public int getLastLevelIndex() {
        return deterministicKey.getChildNumber().num();
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof PublicKey)) {
            return false;
        }

        return deterministicKey.equals(((PublicKey) other).deterministicKey);

    }

    @Override
    public int hashCode() {
        return deterministicKey.hashCode();
    }
}
