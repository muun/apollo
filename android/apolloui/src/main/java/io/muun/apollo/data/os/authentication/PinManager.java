package io.muun.apollo.data.os.authentication;

import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.domain.libwallet.LibwalletClient;
import io.muun.common.utils.Encodings;

import java.util.Arrays;
import javax.inject.Inject;

public class PinManager {

    private static final String PIN_KEY = "pin";
    private static final String PIN_LENGTH_KEY = "pinLength";
    private static final int LEGACY_PIN_LENGTH = 4;

    private final SecureStorageProvider secureStorageProvider;

    private final LibwalletClient libwalletClient;

    @Inject
    public PinManager(
            SecureStorageProvider secureStorageProvider,
            LibwalletClient libwalletClient
    ) {
        this.secureStorageProvider = secureStorageProvider;
        this.libwalletClient = libwalletClient;
    }

    /**
     * Store the pin.
     */
    public void storePin(String pin) {
        secureStorageProvider.put(PIN_KEY, Encodings.stringToBytes(pin));
        libwalletClient.saveInt(PIN_LENGTH_KEY, pin.length());
    }

    public boolean hasPin() {
        return secureStorageProvider.has(PIN_KEY);
    }

    /**
     * Returns the pin length.
     */
    public int getPinLength() {
        // default to LEGACY_PIN_LENGTH when PIN_LENGTH_KEY doesn't exists as old versions of
        // the app didn't save the PIN_LENGTH_KEY key-value.
        return libwalletClient.getInt(PIN_LENGTH_KEY, LEGACY_PIN_LENGTH);
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
