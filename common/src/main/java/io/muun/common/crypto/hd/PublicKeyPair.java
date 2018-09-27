package io.muun.common.crypto.hd;

import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;

public class PublicKeyPair {

    private final PublicKey userPublicKey;
    private final PublicKey muunPublicKey;

    /**
     * Constructor.
     */
    public PublicKeyPair(PublicKey userPublicKey, PublicKey muunPublicKey) {
        this.userPublicKey = userPublicKey;
        this.muunPublicKey = muunPublicKey;

        checkDerivationPaths(userPublicKey, muunPublicKey);
        checkNetworkParameters(userPublicKey, muunPublicKey);
    }

    public PublicKey getUserPublicKey() {
        return userPublicKey;
    }

    public PublicKey getMuunPublicKey() {
        return muunPublicKey;
    }

    public String getAbsoluteDerivationPath() {
        return userPublicKey.getAbsoluteDerivationPath();
    }

    public int getLastLevelIndex() {
        return userPublicKey.getLastLevelIndex();
    }

    public NetworkParameters getNetworkParameters() {
        return userPublicKey.getNetworkParameters();
    }

    /**
     * Derive both PublicKeys from an absolute path.
     */
    public PublicKeyPair deriveFromAbsolutePath(String absolutePath) throws KeyDerivationException {
        return new PublicKeyPair(
                userPublicKey.deriveFromAbsolutePath(absolutePath),
                muunPublicKey.deriveFromAbsolutePath(absolutePath)
        );
    }

    /**
     * Derive both PublicKeys at the same next valid index, starting from {startingIndex}.
     */
    public PublicKeyPair deriveNextValidChild(int startingIndex) {
        int nextIndex = startingIndex;

        while (true) {
            try {
                return deriveChild(nextIndex);
            } catch (KeyDerivationException e) {
                nextIndex++;
            }
        }
    }

    /**
     * Derive both PublicKeys at the same index.
     * @throws KeyDerivationException if the index is invalid.
     */
    public PublicKeyPair deriveChild(int childIndex) throws KeyDerivationException {
        return new PublicKeyPair(
                userPublicKey.deriveChild(childIndex),
                muunPublicKey.deriveChild(childIndex)
        );
    }

    private void checkDerivationPaths(PublicKey userPublicKey, PublicKey muunPublicKey) {
        final String userPath = userPublicKey.getAbsoluteDerivationPath();
        final String muunPath = muunPublicKey.getAbsoluteDerivationPath();

        Preconditions.checkArgument(userPath.equals(muunPath));
    }

    private void checkNetworkParameters(PublicKey userPublicKey, PublicKey muunPublicKey) {
        final NetworkParameters userNetwork = userPublicKey.getNetworkParameters();
        final NetworkParameters muunNetwork = muunPublicKey.getNetworkParameters();

        Preconditions.checkArgument(userNetwork.equals(muunNetwork));
    }
}
