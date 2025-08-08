package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.data.os.OS;

public enum SecureStorageMode {
    J_MODE, M_MODE;

    /**
     * Obtains the mode under which this module is operating, currently:
     * M_MODE For api levels >= 23
     * J_MODE For api levels between 19 and 22.
     */
    public static SecureStorageMode getModeForDevice() {
        return OS.supportsHardwareBackedKeystore()
                ? SecureStorageMode.M_MODE
                : SecureStorageMode.J_MODE;
    }
}