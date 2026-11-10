package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.common.utils.Encodings;

import timber.log.Timber;

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
     * Remove a single value from secure storage.
     */
    public void delete(String key) {
        lock.lock();
        try {
            preferences.delete(key);
            keyStore.deleteEntry(key);

            preferences.recordAuditTrail("DELETE", key);

            if (keyStore.hasKey(key)) {
                // There's reports that sometimes keystore does not delete the key entry.
                // Let's check out and see what we find out.
                Timber.e(new SecureStorageError(debugSnapshot()));
                // deleteEntry is supposed to be idempotent, so...
                keyStore.deleteEntry(key);
            }
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

            // Note: we previously had a precondition here but we see a frequent issue where
            // a key gets erased from prefs but not from keystore.
            // Let's try to avoid crashing in this scenarios and check if Crashlytics or
            // our error report infra offers more metadata/insights on this issue
            if (hasKeyInPreferences != hasKeyInKeystore) {
                final SecureStorageError error;
                if (hasKeyInPreferences) {
                    // The value lost its keystore key (a device-to-device copy is one way this
                    // happens): same error the read path raises for it.
                    error = new KeyStoreCorruptedError(key, debugSnapshot());
                } else {
                    error = new SecureStorageError(debugSnapshot());
                    error.addMetadata("key", key);
                }
                error.addMetadata("hasKeyInPreferences", hasKeyInPreferences);
                error.addMetadata("hasKeyInKeystore", hasKeyInKeystore);
                Timber.e(error);
                throw error;
            }


            return hasKeyInPreferences;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Return `true` if a ciphertext record is stored under the given key. Legacy primitive that
     * exists ONLY to back
     * {@link io.muun.apollo.osmodules.securekv.SecureKeyValueStore#has(String)}: don't add
     * callers, this provider is being phased out behind the securekv store.
     *
     * <p>{@link #has} throws on a prefs/keystore mismatch in either direction, which crashes
     * the app when it runs during Application#onCreate. Here a mismatch is an answer: either
     * half is evidence that something was stored, so either half answers true, and the read
     * that follows classifies the damage. Existence and readability are separate questions,
     * and readability is answered by the securekv error taxonomy alone.
     *
     * <p>Answering false on a surviving keystore key would make erasing the preferences record
     * a way to turn off whatever that record gates. The pin is the case that matters: a plain
     * preferences file is far easier to tamper with than a pin is to guess.
     *
     * <p>A keystore that can't be asked answers false. A probe that failed is not evidence of
     * a record, and treating it as one would put a lock screen in front of someone who never
     * set a pin.
     */
    public boolean hasCiphertext(String key) {
        lock.lock();
        try {
            if (preferences.hasKey(key)) {
                return true;
            }

            return hasOrphanKeystoreEntry(key);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Return `true` if a value is stored under the key but its keystore key is gone (a
     * device-to-device copy is one way this happens). Unlike {@link #has(String)}, it reports that
     * instead of throwing on it, and a failed probe answers `false`: it's meant for callers that
     * act on a `true`.
     */
    public boolean isKeystoreKeyMissing(String key) {
        lock.lock();
        try {
            return preferences.hasKey(key) && !keyStore.hasKey(key);
        } catch (Throwable e) {
            Timber.e(e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    @GuardedBy("lock")
    private boolean hasOrphanKeystoreEntry(String key) {
        final boolean hasKeyInKeystore;

        try {
            hasKeyInKeystore = keyStore.hasKey(key);
        } catch (Throwable e) {
            // hasCiphertext runs during Application#onCreate, where a throw is a crash before
            // any Activity exists.
            Timber.e(e);
            return false;
        }

        if (hasKeyInKeystore) {
            reportOrphanKeystoreEntry(key);
        }

        return hasKeyInKeystore;
    }

    /**
     * Remove all values from secure storage (careful!). Callers must also run
     * RebaseInstallBaselinesAction, see its docs.
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
            Timber.i("InconsistentModeError");
            throw new InconsistentModeError(debugSnapshot());
        }
    }

    @GuardedBy("lock")
    private void throwIfKeyCorruptedOrMissing(String key) {
        final boolean hasKeyInPreferences = preferences.hasKey(key);
        final boolean hasKeyInKeystore = keyStore.hasKey(key);

        if (!hasKeyInKeystore && !hasKeyInPreferences) {
            // Deliberately not logged. Timber.i is the breadcrumb channel, and a key that was
            // never written is the ordinary answer on a logged out app, where the session token
            // is read once per request.
            throw new SecureStorageNoSuchElementError(key, debugSnapshot());
        }

        if (!hasKeyInPreferences) {
            Timber.i("SharedPreferencesCorruptedError for key: " + key);
            throw new SharedPreferencesCorruptedError(key, debugSnapshot());
        }

        if (!hasKeyInKeystore) {
            Timber.i("KeyStoreCorruptedError for key: " + key);
            throw new KeyStoreCorruptedError(key, debugSnapshot());
        }
    }

    @GuardedBy("lock")
    private void reportOrphanKeystoreEntry(String key) {
        try {
            final SecureStorageError error = new SecureStorageError(debugSnapshot());
            error.addMetadata("key", key);
            error.addMetadata("hasKeyInPreferences", false);
            error.addMetadata("hasKeyInKeystore", true);
            Timber.e(error);

        } catch (Throwable e) {
            // Telemetry must not change the answer, and the caller runs during
            // Application#onCreate. The snapshot parses the MODE preference and gets a raw
            // IllegalArgumentException when it holds garbage.
            Timber.e(e);
        }
    }

    @GuardedBy("lock")
    private byte[] retrieveDecrypted(String key) {
        try {
            return keyStore.decryptData(preferences.getBytes(key), key, preferences.getAesIv(key));
        } catch (Throwable e) {
            Timber.i("SecureStorageError on READ for key: " + key);
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
            Timber.i("SecureStorageError on WRITE for key: " + key);
            Timber.e(e);
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
