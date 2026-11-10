package io.muun.common.crypto.hd;

import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;

public class PublicKeyPair {

    private final PublicKey userPublicKey;
    private final PublicKey cosignerPublicKey;

    /**
     * Constructor.
     */
    public PublicKeyPair(PublicKey userPublicKey, PublicKey cosignerPublicKey) {
        this.userPublicKey = userPublicKey;
        this.cosignerPublicKey = cosignerPublicKey;

        checkDerivationPaths(userPublicKey, cosignerPublicKey);
        checkNetworkParameters(userPublicKey, cosignerPublicKey);
    }

    public PublicKey getUserPublicKey() {
        return userPublicKey;
    }

    public PublicKey getCosignerPublicKey() {
        return cosignerPublicKey;
    }

    /**
     * Legacy name for {@link #getCosignerPublicKey()}: apollo still calls it.
     */
    @Deprecated
    public PublicKey getMuunPublicKey() {
        return cosignerPublicKey;
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
                cosignerPublicKey.deriveFromAbsolutePath(absolutePath)
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
                cosignerPublicKey.deriveChild(childIndex)
        );
    }

    private void checkDerivationPaths(PublicKey userPublicKey, PublicKey cosignerPublicKey) {
        final String userPath = userPublicKey.getAbsoluteDerivationPath();
        final String cosignerPath = cosignerPublicKey.getAbsoluteDerivationPath();

        Preconditions.checkArgument(userPath.equals(cosignerPath));
    }

    private void checkNetworkParameters(PublicKey userPublicKey, PublicKey cosignerPublicKey) {
        final NetworkParameters userNetwork = userPublicKey.getNetworkParameters();
        final NetworkParameters cosignerNetwork = cosignerPublicKey.getNetworkParameters();

        Preconditions.checkArgument(userNetwork.equals(cosignerNetwork));
    }
}
