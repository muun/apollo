package io.muun.apollo.data.os.secure_storage;

import android.os.Build;

public enum SecureStorageMode {
    J_MODE, M_MODE;

    /**
     * Obtains the mode under which this module is operating, currently:
     * M_MODE For api levels >= 23
     * J_MODE For api levels between 19 and 22.
     */
    public static SecureStorageMode getModeForDevice() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                ? SecureStorageMode.J_MODE
                : SecureStorageMode.M_MODE;
    }
}