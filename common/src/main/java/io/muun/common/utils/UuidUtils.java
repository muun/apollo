package io.muun.common.utils;

import java.util.UUID;

public final class UuidUtils {

    private UuidUtils() {
        throw new AssertionError();
    }

    /**
     * Check whether a given string is a valid UUID.
     */
    public static boolean isValidUuid(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
