package io.muun.common.crypto.hd;

import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;

public class PublicKeyTriple {

    private final PublicKey userPublicKey;
    private final PublicKey muunPublicKey;
    private final PublicKey swapServerPublicKey;

    /**
     * Constructor.
     */
    public PublicKeyTriple(PublicKey userPublicKey,
                           PublicKey muunPublicKey,
                           PublicKey swapServerPublicKey) {
        this.userPublicKey = userPublicKey;
        this.muunPublicKey = muunPublicKey;
        this.swapServerPublicKey = swapServerPublicKey;

        checkDerivationPaths();
        checkNetworkParameters();
    }

    public PublicKey getUserPublicKey() {
        return userPublicKey;
    }

    public PublicKey getMuunPublicKey() {
        return muunPublicKey;
    }

    public PublicKey getSwapServerPublicKey() {
        return swapServerPublicKey;
    }

    public String getAbsoluteDerivationPath() {
        return userPublicKey.getAbsoluteDerivationPath();
    }

    public int getLastLevelIndex() {
        return userPublicKey.getLastLevelIndex();
    }

    /**
     * Deprecated and commented out as a deterrent. This method "falls short" for regtest addresses:
     * as it uses the base58 serialization to read the data, it does not distinguish between testnet
     * and regtest addresses. Leaving the code commented in case someone in the future has the same
     * idea.
     */
    //public NetworkParameters getNetworkParameters() {
    //    return userPublicKey.getNetworkParameters();
    //}

    /**
     * Derive all PublicKeys from an absolute path.
     */
    public PublicKeyTriple deriveFromAbsolutePath(String absolutePath)
            throws KeyDerivationException {

        return new PublicKeyTriple(
                userPublicKey.deriveFromAbsolutePath(absolutePath),
                muunPublicKey.deriveFromAbsolutePath(absolutePath),
                swapServerPublicKey.deriveFromAbsolutePath(absolutePath));
    }

    /**
     * Derive all PublicKeys at the same next valid index, starting from {startingIndex}.
     */
    public PublicKeyTriple deriveNextValidChild(int startingIndex) {
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
     * Derive all PublicKeys at the same index.
     * @throws KeyDerivationException if the index is invalid.
     */
    public PublicKeyTriple deriveChild(int childIndex) throws KeyDerivationException {
        return new PublicKeyTriple(
                userPublicKey.deriveChild(childIndex),
                muunPublicKey.deriveChild(childIndex),
                swapServerPublicKey.deriveChild(childIndex));
    }

    public PublicKeyPair toPair() {
        return new PublicKeyPair(userPublicKey, muunPublicKey);
    }

    private void checkDerivationPaths() {
        final String userPath = userPublicKey.getAbsoluteDerivationPath();
        final String muunPath = muunPublicKey.getAbsoluteDerivationPath();
        final String swapServerPath = swapServerPublicKey.getAbsoluteDerivationPath();

        Preconditions.checkArgument(userPath.equals(muunPath));
        Preconditions.checkArgument(muunPath.equals(swapServerPath));
    }

    private void checkNetworkParameters() {
        final NetworkParameters userNetwork = userPublicKey.getNetworkParameters();
        final NetworkParameters muunNetwork = muunPublicKey.getNetworkParameters();
        final NetworkParameters swapServerNetwork = swapServerPublicKey.getNetworkParameters();

        Preconditions.checkArgument(userNetwork.equals(muunNetwork));
        Preconditions.checkArgument(muunNetwork.equals(swapServerNetwork));
    }
}
