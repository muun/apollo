package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.common.utils.Preconditions;

import rx.Observable;

import java.util.NoSuchElementException;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SecureStorageProvider {

    private final KeyStoreProvider keyStore;
    private final SecureStoragePreferences preferences;

    /**
     * Constructor.
     */
    @Inject
    public SecureStorageProvider(KeyStoreProvider keyStore,
                                 SecureStoragePreferences preferences) {
        this.keyStore = keyStore;
        this.preferences = preferences;
    }

    /**
     * Fetch and decrypt a value from secure storage.
     */
    public byte[] get(String key) {
        throwIfModeInconsistent();
        throwIfKeyCorruptedOrMissing(key);

        try {
            return retrieveDecrypted(key);

        } catch (Throwable e) {
            throw new SecureStorageError(e);
        }
    }

    /**
     * Like `get(key)`, but asynchronous.
     */
    public Observable<byte[]> getAsync(String key) {
        return Observable.fromCallable(() -> get(key));
    }

    /**
     * Encrypt and save a value in secure storage.
     */
    public void put(String key, byte[] value) {
        throwIfModeInconsistent();

        try {
            storeEncrypted(key, value);

        } catch (Throwable e) {
            throw new SecureStorageError(e);
        }
    }

    /**
     * Like `put(key, value)`, but asynchronous.
     */
    public Observable<Void> putAsync(String key, byte[] value) {
        return Observable.fromCallable(() -> {
            put(key, value);
            return null;
        });
    }

    /**
     * Remove a single value from secure storage.
     */
    public void delete(String key) {
        preferences.delete(key);
        keyStore.deleteEntry(key);
    }

    /**
     * Return `true` if the key exists in secure storage.
     */
    public boolean has(String key) {
        final boolean hasKeyInPreferences = preferences.hasKey(key);
        final boolean hasKeyInKeystore = keyStore.hasKey(key);

        Preconditions.checkState(hasKeyInPreferences == hasKeyInKeystore);

        return hasKeyInPreferences;
    }

    /**
     * Remove all values from secure storage (careful!).
     */
    public void wipe() {
        preferences.wipe();
        keyStore.wipe();
    }

    private void throwIfModeInconsistent() {
        if (!preferences.isCompatibleFormat()) {
            throw new InconsistentModeError();
        }
    }

    private void throwIfKeyCorruptedOrMissing(String key) {
        final boolean hasKeyInPreferences = preferences.hasKey(key);
        final boolean hasKeyInKeystore = keyStore.hasKey(key);

        if (!hasKeyInKeystore && !hasKeyInPreferences) {
            throw new NoSuchElementException();
        }

        if (!hasKeyInPreferences) {
            throw new SharedPreferencesCorruptedError();
        }

        if (!hasKeyInKeystore) {
            throw new KeyStoreCorruptedError();
        }
    }

    private byte[] retrieveDecrypted(String key) {
        return keyStore.decryptData(preferences.getBytes(key), key, preferences.getAesIv(key));
    }

    private void storeEncrypted(String key, byte[] input) {
        preferences.saveBytes(keyStore.encryptData(input, key, preferences.getAesIv(key)), key);
    }

    /**
     * The Android KeyStore appears to be corrupted: a key present in our Preference map is missing.
     */
    public class KeyStoreCorruptedError extends SecureStorageError {
        public KeyStoreCorruptedError(Throwable throwable) {
            super(throwable);
        }

        public KeyStoreCorruptedError() {
        }
    }

    /**
     * The SharedPreferences bag appears to be corrupted: a key present in our KeyStore is missing.
     */
    public class SharedPreferencesCorruptedError extends SecureStorageError {
        public SharedPreferencesCorruptedError(Throwable throwable) {
            super(throwable);
        }

        public SharedPreferencesCorruptedError() {
        }
    }

    /**
     * An operation was attempted using a SecureStorageMode different from the one used last time.
     * This is most likely due to a system update to Marshmallow from a previous version.
     */
    public class InconsistentModeError extends SecureStorageError {
        public InconsistentModeError(Throwable throwable) {
            super(throwable);
        }

        public InconsistentModeError() {
        }
    }
}
