package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.common.rx.RxHelper;

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
     * @param key      Key alias under which the secret was stored.
     * @return Secret which was encrypted under this storage.
     */
    public Observable<byte[]> get(String key) {
        try {
            if (!preferences.isCompatibleFormat()) {
                return Observable.error(new InconsistentModeError());
            }

            throwIfKeyCorruptedOrMissing(key);

            return Observable.fromCallable(() -> retrieveDecrypted(key))
                    .onErrorResumeNext(throwable ->
                            Observable.error(new SecureStorageError(throwable)));
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    /**
     * Store a given secret in the store.
     *
     * @param key      Key alias under which this secret will be stored.
     * @param input    secret to store.
     * @return encrypted secret.
     */
    public Observable<Void> put(String key, byte[] input) {
        try {
            if (!preferences.isCompatibleFormat()) {
                return Observable.error(new InconsistentModeError());
            }

            return Observable
                    .fromCallable(() -> {
                        storeEncrypted(input, key);
                        return null;
                    })
                    .map(RxHelper::toVoid)
                    .onErrorResumeNext(error -> Observable.error(new SecureStorageError(error)));

        } catch (Exception e) {
            return Observable.error(new SecureStorageError(e));
        }
    }

    public void delete(String key) {
        preferences.delete(key);
        keyStore.deleteEntry(key);
    }

    public boolean has(String key) {
        return keyStore.hasKey(key)
                && preferences.hasKey(key);
    }

    public void wipe() {
        preferences.wipe();
        keyStore.wipe();
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
        return keyStore.decryptData(
                preferences.getBytes(key),
                key,
                preferences.getAesIv(key));
    }

    private void storeEncrypted(byte[] input, String key) {
        preferences.saveBytes(
                keyStore.encryptData(input, key, preferences.getAesIv(key)),
                key
        );
    }

    /**
     * This exception is raised when keystore appears to be corrupted, lacking data that other
     * storages has, you can either delete the conflicting key with {@link #delete(String)} or wipe
     * the secure storage with {@link #wipe()}.
     */
    public class KeyStoreCorruptedError extends SecureStorageError {
        public KeyStoreCorruptedError(Throwable throwable) {
            super(throwable);
        }

        public KeyStoreCorruptedError() { }
    }

    /**
     * This exception is raised when sharedpreferences appears to be corrupted, lacking data that
     * other storages has, you can either delete the conflicting key with {@link #delete(String)} or
     * wipe the secure storage with {@link #wipe()}.
     */
    public class SharedPreferencesCorruptedError extends SecureStorageError {
        public SharedPreferencesCorruptedError(Throwable throwable) {
            super(throwable);
        }

        public SharedPreferencesCorruptedError() { }
    }

    /**
     * This exception is raised when trying to perform an operation in a secure storage that has
     * been initialized in another mode. Most likely due to a system update to marshmallow from a
     * previous version, automatic migration is not currently supported, you can wipe the secure
     * storage with{@link #wipe()}.
     */
    public class InconsistentModeError extends SecureStorageError {
        public InconsistentModeError(Throwable throwable) {
            super(throwable);
        }

        public InconsistentModeError() { }
    }
}
