package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.common.utils.RandomGenerator;

import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SecureStoragePreferences {
    private static final int AES_IV_SIZE = 16;
    private static final String AES_IV_KEY_PREFIX = "aes_iv_";
    private static final String STORAGE_NAME = "muun-secure-storage";
    private static final String MODE = "mode";

    private final SharedPreferences sharedPreferences;

    @Inject
    public SecureStoragePreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE);
    }

    private void initSecureStorage() {
        if (!sharedPreferences.contains(MODE)) {
            sharedPreferences.edit().putString(MODE, getMode().name()).apply();
        }
    }

    public byte[] getAesIv(String key) {
        return getPersistentSecureRandomBytes(AES_IV_KEY_PREFIX + key, AES_IV_SIZE);
    }

    /**
     * Obtains a random sequence of bytes, this will be persisted under this module.
     */
    public synchronized byte[] getPersistentSecureRandomBytes(String key, int size) {
        if (sharedPreferences.contains(key)) {
            return getBytes(key);

        } else {
            final byte[] bytes = RandomGenerator.getBytes(size);
            saveBytes(bytes, key);
            return bytes;
        }
    }

    /**
     * Saves data inside this module.
     */
    public void saveBytes(byte[] bytes, String key) {
        initSecureStorage();

        sharedPreferences.edit().putString(key, SerializationUtils.serializeBytes(bytes)).apply();
    }

    /**
     * Obtains data from this module.
     */
    public byte[] getBytes(String key) {
        return SerializationUtils.deserializeBytes(sharedPreferences.getString(key, ""));
    }

    /**
     * Returns true if this module contains data under the given key.
     */
    public boolean hasKey(String key) {
        return sharedPreferences.contains(key);
    }

    /**
     * Wipes all data from this module.
     */
    public void wipe() {
        sharedPreferences.edit().clear().apply();
    }

    /**
     * Obtains the mode under which this module is operating, currently:
     * M_MODE For api levels >= 23
     * J_MODE For api levels between 19 and 22.
     */
    public SecureStorageMode getMode() {
        //This method exists for testing purposes.
        return SecureStorageMode.getModeForDevice();
    }

    /**
     * @return true if this module is currently storing data under the same mode as which is
     *      currently operating or hasn't being initialized.
     */
    public boolean isCompatibleFormat() {

        //TODO: Should work with proguard, but check just in case ...
        return !sharedPreferences.contains(MODE)
                || SecureStorageMode.valueOf(sharedPreferences.getString(MODE, "")) == getMode();
    }

    /**
     * Deletes all data associated with this key stored in this module.
     */
    public void delete(String key) {
        sharedPreferences
                .edit()
                .remove(key)
                .apply();
    }
}
