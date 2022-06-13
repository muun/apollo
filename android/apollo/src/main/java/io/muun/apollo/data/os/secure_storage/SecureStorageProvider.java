package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Preconditions;

import androidx.annotation.VisibleForTesting;
import rx.Observable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SecureStorageProvider {

    @GuardedBy("lock")
    private final KeyStoreProvider keyStore;

    @GuardedBy("lock")
    private final SecureStoragePreferences preferences;

    private final Lock lock;

    /**
     * Constructor.
     */
    @Inject
    public SecureStorageProvider(KeyStoreProvider keyStore,
                                 SecureStoragePreferences preferences) {
        this.keyStore = keyStore;
        this.preferences = preferences;
        this.lock = new ReentrantLock();
    }

    /**
     * Fetch and decrypt a value from secure storage.
     */
    public byte[] get(String key) {
        lock.lock();
        try {
            throwIfModeInconsistent();
            throwIfKeyCorruptedOrMissing(key);

            return retrieveDecrypted(key);
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            throwIfModeInconsistent();

            storeEncrypted(key, value);

            preferences.recordAuditTrail("PUT", key);
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            preferences.delete(key);
            keyStore.deleteEntry(key);

            preferences.recordAuditTrail("DELETE", key);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Return `true` if the key exists in secure storage.
     */
    public boolean has(String key) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove all values from secure storage (careful!).
     */
    public void wipe() {
        lock.lock();
        try {
            preferences.wipe();
            keyStore.wipe();

            preferences.recordAuditTrail("WIPE", "*");
        } finally {
            lock.unlock();
        }
    }

    @GuardedBy("lock")
    private void throwIfModeInconsistent() {
        if (!preferences.isCompatibleFormat()) {
            throw new InconsistentModeError(debugSnapshot());
        }
    }

    @GuardedBy("lock")
    private void throwIfKeyCorruptedOrMissing(String key) {
        final boolean hasKeyInPreferences = preferences.hasKey(key);
        final boolean hasKeyInKeystore = keyStore.hasKey(key);

        if (!hasKeyInKeystore && !hasKeyInPreferences) {
            throw new SecureStorageNoSuchElementError(key, debugSnapshot());
        }

        if (!hasKeyInPreferences) {
            throw new SharedPreferencesCorruptedError(key, debugSnapshot());
        }

        if (!hasKeyInKeystore) {
            throw new KeyStoreCorruptedError(key, debugSnapshot());
        }
    }

    @GuardedBy("lock")
    private byte[] retrieveDecrypted(String key) {
        try {
            return keyStore.decryptData(preferences.getBytes(key), key, preferences.getAesIv(key));
        } catch (Throwable e) {
            final SecureStorageError ssError = new SecureStorageError(e, debugSnapshot());
            enhanceError(ssError, key);
            throw ssError;
        }
    }

    @GuardedBy("lock")
    private void storeEncrypted(String key, byte[] input) {
        try {
            preferences.saveBytes(keyStore.encryptData(input, key, preferences.getAesIv(key)), key);
        } catch (Throwable e) {
            final SecureStorageError ssError = new SecureStorageError(e, debugSnapshot());
            enhanceError(ssError, key);
            throw ssError;
        }
    }

    private void enhanceError(SecureStorageError ssError, String key) {
        ssError.addMetadata("key", key);
        ssError.addMetadata("cypherText", Encodings.bytesToHex(preferences.getBytes(key)));
        ssError.addMetadata("aesIV", Encodings.bytesToHex(preferences.getAesIv(key)));
    }

    /**
     * Take a debug snapshot of the current state of the secure storage. This is safe to
     * report without compromising any user data.
     */
    @VisibleForTesting
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
     * The key trying to be accessed is not present in our Secure Storage: not in the Android
     * Keystore, nor in our SharedPreferences.
     */
    public static class SecureStorageNoSuchElementError extends SecureStorageError {
        public SecureStorageNoSuchElementError(String key, DebugSnapshot debugSnapshot) {
            super(debugSnapshot);
            addMetadata("key", key);
        }
    }

    /**
     * The Android KeyStore appears to be corrupted: a key present in our Preference map is missing.
     */
    public static class KeyStoreCorruptedError extends SecureStorageError {
        public KeyStoreCorruptedError(String key, DebugSnapshot debugSnapshot) {
            super(debugSnapshot);
            addMetadata("key", key);
        }
    }

    /**
     * The SharedPreferences bag appears to be corrupted: a key present in our KeyStore is missing.
     */
    public static class SharedPreferencesCorruptedError extends SecureStorageError {
        public SharedPreferencesCorruptedError(String key, DebugSnapshot debugSnapshot) {
            super(debugSnapshot);
            addMetadata("key", key);
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
