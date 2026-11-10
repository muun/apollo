package io.muun.common.crypto.hd;

import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.utils.Preconditions;

import com.google.common.annotations.VisibleForTesting;
import org.bitcoinj.core.NetworkParameters;

import java.util.List;

/**
 * Public keys used to build an address.
 */
public class PublicKeyTriple {

    private final PublicKey userPublicKey;

    private final PublicKey cosignerPublicKey;

    private final PublicKey lightningServerPublicKey;

    /**
     * Constructor.
     */
    public PublicKeyTriple(
            PublicKey userPublicKey,
            PublicKey cosignerPublicKey,
            PublicKey lightningServerPublicKey
    ) {
        this.userPublicKey = userPublicKey;
        this.cosignerPublicKey = cosignerPublicKey;
        this.lightningServerPublicKey = lightningServerPublicKey;

        checkDerivationPaths();
        checkNetworkParameters();
    }

    public PublicKey getUserPublicKey() {
        return userPublicKey;
    }

    public PublicKey getCosignerPublicKey() {
        return cosignerPublicKey;
    }

    public PublicKey getLightningServerPublicKey() {
        return lightningServerPublicKey;
    }

    /**
     * Legacy name for {@link #getCosignerPublicKey()}: apollo still calls it.
     */
    @Deprecated
    public PublicKey getMuunPublicKey() {
        return cosignerPublicKey;
    }

    // TODO: delete in the Apollo switch, once nothing in-repo calls it.
    // Temporary backward compat for old Apollo builds. Returns cosigning key (same path).
    @Deprecated
    public PublicKey getSwapServerPublicKey() {
        return cosignerPublicKey;
    }

    /**
     * Return the shared user/cosigner path.
     */
    public String getAbsoluteDerivationPath() {
        return userPublicKey.getAbsoluteDerivationPath();
    }

    public int getLastLevelIndex() {
        return userPublicKey.getLastLevelIndex();
    }

    @SuppressWarnings({"DanglingJavadoc", "MissingJavadocMethod"})
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
     * Derive user/cosigner to the target and Lightning by the same relative steps.
     */
    public PublicKeyTriple deriveFromAbsolutePath(String absolutePath)
            throws KeyDerivationException {

        final PublicKey derivedLightningServerKey =
                deriveLightningServerKeyIgnoringBasePath(absolutePath);

        return new PublicKeyTriple(
                userPublicKey.deriveFromAbsolutePath(absolutePath),
                cosignerPublicKey.deriveFromAbsolutePath(absolutePath),
                derivedLightningServerKey
        );
    }

    private PublicKey deriveLightningServerKeyIgnoringBasePath(String absolutePath)
            throws KeyDerivationException {

        // User/cosigner and Lightning can have different bases. For example, with bases
        // m/1'/1' and m, a user target of m/1'/1'/1/7 maps to Lightning target m/1/7.
        final List<ChildNumber> userBasePath = DerivationPathUtils.parsePath(
                userPublicKey.getAbsoluteDerivationPath()
        );
        final List<ChildNumber> targetPath = DerivationPathUtils.parsePath(absolutePath);

        // The target must equal or descend from the user's base path, ignoring labels.
        Preconditions.checkArgument(
                targetPath.size() >= userBasePath.size()
                        && targetPath.subList(0, userBasePath.size()).equals(userBasePath)
        );

        // Append only the steps below the user base to the current Lightning path.
        final StringBuilder lightningServerTargetPath = new StringBuilder(
                lightningServerPublicKey.getAbsoluteDerivationPath()
        );
        for (ChildNumber step : targetPath.subList(userBasePath.size(), targetPath.size())) {
            lightningServerTargetPath.append('/').append(step);
        }

        return lightningServerPublicKey.deriveFromAbsolutePath(
                lightningServerTargetPath.toString()
        );
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
     *
     * @throws KeyDerivationException if the index is invalid.
     */
    public PublicKeyTriple deriveChild(int childIndex) throws KeyDerivationException {
        return new PublicKeyTriple(
                userPublicKey.deriveChild(childIndex),
                cosignerPublicKey.deriveChild(childIndex),
                lightningServerPublicKey.deriveChild(childIndex)
        );
    }

    /**
     * Downgrade to a key pair.
     *
     * <p>This most likely means legacy code. Consider migrating it instead of using this.
     */
    @VisibleForTesting
    public PublicKeyPair toPair() {
        return new PublicKeyPair(userPublicKey, cosignerPublicKey);
    }

    /**
     * Check that user and cosigner share a path.
     */
    private void checkDerivationPaths() {
        checkDerivationPaths(userPublicKey.getAbsoluteDerivationPath());
    }

    /**
     * Check that user and cosigner are at the given path.
     */
    public void checkDerivationPaths(final String derivationPath) {
        // The Lightning key may have a different base path.
        Preconditions.checkArgument(
                derivationPath.equals(userPublicKey.getAbsoluteDerivationPath())
        );
        Preconditions.checkArgument(
                derivationPath.equals(cosignerPublicKey.getAbsoluteDerivationPath())
        );
    }


    private void checkNetworkParameters() {
        final NetworkParameters userNetwork = userPublicKey.getNetworkParameters();
        final NetworkParameters cosignerNetwork = cosignerPublicKey.getNetworkParameters();
        final NetworkParameters lightningServerNetwork =
                lightningServerPublicKey.getNetworkParameters();

        Preconditions.checkArgument(userNetwork.equals(cosignerNetwork));
        Preconditions.checkArgument(cosignerNetwork.equals(lightningServerNetwork));
    }
}
