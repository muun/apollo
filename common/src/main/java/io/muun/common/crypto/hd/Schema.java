package io.muun.common.crypto.hd;

import io.muun.common.utils.Preconditions;

import java.util.Arrays;
import java.util.List;

public class Schema {

    private static final String BASE_PATH = "m/schema:1'/recovery:1'";

    public enum DerivationBranches {
        CHANGE(0),
        EXTERNAL(1),
        CONTACTS(2),
        METADATA(3);

        public final int index;

        DerivationBranches(int index) {
            this.index = index;
        }
    }

    // Only these branches appear in the Emergency Kit's output descriptors.
    private static final List<DerivationBranches> DESCRIPTOR_ADDRESS_BRANCHES =
            Arrays.asList(DerivationBranches.CHANGE, DerivationBranches.EXTERNAL);

    /**
     * Returns the path to derive a user's key from which change addresses are going to be derived.
     */
    public static String getChangeKeyPath() {
        return BASE_PATH + "/change:" + DerivationBranches.CHANGE.index;
    }

    /**
     * Returns the path to derive a user's key from which external addresses are going to be
     * derived.
     */
    public static String getExternalKeyPath() {
        return BASE_PATH + "/external:" + DerivationBranches.EXTERNAL.index;
    }

    /**
     * Returns the path to derive any number of keys to be used by a given contact, to send money to
     * this user.
     */
    public static String getContactsKeyPath() {
        return BASE_PATH + "/contacts:" + DerivationBranches.CONTACTS.index;
    }

    public static String getMetadataKeyPath() {
        return BASE_PATH + "/metadata:" + DerivationBranches.METADATA.index;
    }

    /**
     * Returns the list of subtree paths that MUST be derivable from a root key.
     */
    public static List<String> getAllSubtreePaths() {
        return Arrays.asList(
                getChangeKeyPath(),
                getExternalKeyPath(),
                getContactsKeyPath(),
                getMetadataKeyPath()
        );
    }

    /**
     * Validate an address derivation path. A valid path is one the Emergency Kit's output
     * descriptors cover: those end in {@code /<branch>/*}, so it must look like
     * {@code m/schema:1'/recovery:1'/<branch>/<index>}. The descriptors live in
     * {@code libwallet/emergencykit/descriptors.go}.
     */
    public static void validateAddressDerivationPath(final String absoluteDerivationPath) {
        final List<ChildNumber> basePath = DerivationPathUtils.parsePath(BASE_PATH);
        final List<ChildNumber> path = DerivationPathUtils.parsePath(absoluteDerivationPath);

        final boolean matchesDescriptors =
                // <branch> and <index> are the only levels below m/schema:1'/recovery:1'.
                path.size() == basePath.size() + 2
                // The path starts at m/schema:1'/recovery:1'.
                && path.subList(0, basePath.size()).equals(basePath)
                // <branch> is change:0 or external:1, never hardened.
                && isDescriptorAddressBranch(path.get(basePath.size()))
                // <index> is what `/*` stands for, and `/*` never covers hardened children.
                && !path.get(basePath.size() + 1).isHardened();

        Preconditions.checkArgument(
                matchesDescriptors,
                absoluteDerivationPath + " matches no output descriptor"
        );
    }

    // Comparing whole ChildNumbers also rejects a hardened branch level. No streams here:
    // common is animalsniffer-checked against an Android API level without java.util.stream.
    private static boolean isDescriptorAddressBranch(final ChildNumber level) {
        for (final DerivationBranches branch : DESCRIPTOR_ADDRESS_BRANCHES) {
            if (level.equals(new ChildNumber(branch.index, false, null))) {
                return true;
            }
        }
        return false;
    }

    public static String getBasePath() {
        return BASE_PATH;
    }
}
