package io.muun.common.model;

/**
 * A list of the currently supported Emergency Kit versions (and what they represent).
 * Note to maintainer: this needs to be in-sync with Libwallet's exported constants. See:
 * libwallet/emergency_kit.go
 */
public class EkVersion {
    // Pre-PDF era when the user had to write down the keys
    public static final int VERSION_ONLY_KEYS = 1;
    // First PDF, including the output descriptors
    public static final int VERSION_DESCRIPTORS = 2;
    // Second PDF, adding the musig descriptors
    public static final int VERSION_MUSIG = 3;
}
