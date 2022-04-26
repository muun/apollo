package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.common.utils.Preconditions;

import rx.Observable;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.Nullable;
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
            throw new SecureStorageError(e, debugSnapshot());
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
            throw new SecureStorageError(e, debugSnapshot());
        }

        preferences.recordAuditTrail("PUT", key);
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

        preferences.recordAuditTrail("DELETE", key);
    }

    /**
     * Return `true` if the key exists in secure storage.
     */
    public boolean has(String key) {
        final boolean hasKeyInPreferences = preferences.hasKey(key);
        final boolean hasKeyInKeystore = keyStore.hasKey(key);

        Preconditions.checkState(
                hasKeyInPreferences == hasKeyInKeystore,
                String.format(
                        "IllegalState: key =%s, hasKeyInPreferences =%s, hasKeyInKeystore =%s",
                        key,
                        hasKeyInPreferences,
                        hasKeyInKeystore
                )
        );

        return hasKeyInPreferences;
    }

    /**
     * Remove all values from secure storage (careful!).
     */
    public void wipe() {
        preferences.wipe();
        keyStore.wipe();

        preferences.recordAuditTrail("WIPE", "*");
    }

    private void throwIfModeInconsistent() {
        if (!preferences.isCompatibleFormat()) {
            throw new InconsistentModeError(debugSnapshot());
        }
    }

    private void throwIfKeyCorruptedOrMissing(String key) {
        final boolean hasKeyInPreferences = preferences.hasKey(key);
        final boolean hasKeyInKeystore = keyStore.hasKey(key);

        if (!hasKeyInKeystore && !hasKeyInPreferences) {
            throw new NoSuchElementException();
        }

        if (!hasKeyInPreferences) {
            throw new SharedPreferencesCorruptedError(debugSnapshot());
        }

        if (!hasKeyInKeystore) {
            throw new KeyStoreCorruptedError(debugSnapshot());
        }
    }

    private byte[] retrieveDecrypted(String key) {
        return keyStore.decryptData(preferences.getBytes(key), key, preferences.getAesIv(key));
    }

    private void storeEncrypted(String key, byte[] input) {
        preferences.saveBytes(keyStore.encryptData(input, key, preferences.getAesIv(key)), key);
    }

    /**
     * Take a debug snapshot of the current state of the secure storage. This is safe to
     * report without compromising any user data.
     */
    public DebugSnapshot debugSnapshot() {
        // NEVER ever return any values from the keystore itself, only labels should get out.

        Set<String> keystoreLabels = null;
        Exception keystoreException = null;
        try {
            keystoreLabels = keyStore.getAllLabels();
        } catch (final Exception e) {
            keystoreException = e;
        }

        return new DebugSnapshot(
                preferences.getMode(),
                preferences.isCompatibleFormat(),
                preferences.getAllLabels(),
                preferences.getAllIvLabels(),
                keystoreLabels,
                keystoreException,
                preferences.getAuditTrail()
        );
    }

    /**
     * The Android KeyStore appears to be corrupted: a key present in our Preference map is missing.
     */
    public static class KeyStoreCorruptedError extends SecureStorageError {
        public KeyStoreCorruptedError(DebugSnapshot debugSnapshot) {
            super(debugSnapshot);
        }
    }

    /**
     * The SharedPreferences bag appears to be corrupted: a key present in our KeyStore is missing.
     */
    public static class SharedPreferencesCorruptedError extends SecureStorageError {
        public SharedPreferencesCorruptedError(DebugSnapshot debugSnapshot) {
            super(debugSnapshot);
        }
    }

    /**
     * An operation was attempted using a SecureStorageMode different from the one used last time.
     * This is most likely due to a system update to Marshmallow from a previous version.
     */
    public static class InconsistentModeError extends SecureStorageError {
        public InconsistentModeError(DebugSnapshot debugSnapshot) {
            super(debugSnapshot);
        }
    }

    public static class DebugSnapshot {
        public final SecureStorageMode mode;
        public final boolean isCompatible;
        public final Set<String> labelsInPrefs;
        public final Set<String> labelsWithIvInPrefs;
        public @Nullable final Set<String> labelsInKeystore;
        public @Nullable final Exception keystoreException;
        public final List<String> auditTrail;

        public DebugSnapshot(
                final SecureStorageMode mode,
                final boolean isCompatible,
                final Set<String> labelsInPrefs,
                final Set<String> labelsWithIvInPrefs,
                @Nullable final Set<String> labelsInKeystore,
                @Nullable final Exception keystoreException,
                final List<String> auditTrail
        ) {
            this.mode = mode;
            this.isCompatible = isCompatible;
            this.labelsInPrefs = labelsInPrefs;
            this.labelsWithIvInPrefs = labelsWithIvInPrefs;
            this.labelsInKeystore = labelsInKeystore;
            this.keystoreException = keystoreException;
            this.auditTrail = auditTrail;
        }
    }
}
