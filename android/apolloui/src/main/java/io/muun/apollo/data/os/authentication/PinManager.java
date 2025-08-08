package io.muun.apollo.data.os.authentication;

import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.common.utils.Encodings;

import java.util.Arrays;
import javax.inject.Inject;

public class PinManager {
    private static final String PIN_KEY = "pin";

    private final SecureStorageProvider secureStorageProvider;

    @Inject
    public PinManager(SecureStorageProvider secureStorageProvider) {
        this.secureStorageProvider = secureStorageProvider;
    }

    /**
     * Store the pin.
     */
    public void storePin(String pin) {
        secureStorageProvider.put(PIN_KEY, Encodings.stringToBytes(pin));
    }

    public boolean hasPin() {
        return secureStorageProvider.has(PIN_KEY);
    }

    /**
     * Returns true if the given pin is the same as the stored one.
     */
    public boolean verifyPin(String pin) {
        final byte[] pinBytes = Encodings.stringToBytes(pin);
        final byte[] storedPinBytes = secureStorageProvider.get(PIN_KEY);

        return Arrays.equals(storedPinBytes, pinBytes);
    }
}
