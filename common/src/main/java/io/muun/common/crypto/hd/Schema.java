package io.muun.common.crypto.hd;

import java.util.Arrays;
import java.util.List;

public class Schema {

    private static final String BASE_PATH = "m/schema:1'/recovery:1'";

    public enum DerivationBranches {
        CHANGE(0),
        EXTERNAL(1),
        CONTACTS(2);

        public final int index;

        DerivationBranches(int index) {
            this.index = index;
        }
    }

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

    /**
     * Returns the list of subtree paths that MUST be derivable from a root key.
     */
    public static List<String> getAllSubtreePaths() {
        return Arrays.asList(
                getChangeKeyPath(),
                getExternalKeyPath(),
                getContactsKeyPath()
        );
    }

    public static String getBasePath() {
        return BASE_PATH;
    }
}
