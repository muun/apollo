package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.data.preferences.adapter.JsonListPreferenceAdapter;
import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.data.preferences.rx.RxSharedPreferences;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.common.crypto.CryptographyException;
import io.muun.common.utils.Dates;
import io.muun.common.utils.RandomGenerator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.common.annotations.VisibleForTesting;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@SuppressLint("ApplySharedPref") // allow disk writes to block execution (important here)
public class SecureStoragePreferences {
    private static final int AES_IV_SIZE = 16;
    private static final String AES_IV_KEY_PREFIX = "aes_iv_";
    private static final String STORAGE_NAME = "muun-secure-storage";
    private static final String MODE = "mode";
    private static final String AUDIT_TRAIL_STORAGE_NAME = "audit-trail";
    private static final String AUDIT_TRAIL_KEY = "audit-trail";

    private final SharedPreferences sharedPreferences;
    private final Preference<List<String>> auditPreference;

    @Inject
    public SecureStoragePreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE);
        final SharedPreferences auditTrailPreferences = context.getSharedPreferences(
                AUDIT_TRAIL_STORAGE_NAME,
                Context.MODE_PRIVATE
        );
        auditPreference = RxSharedPreferences.create(auditTrailPreferences).<List<String>>getObject(
                AUDIT_TRAIL_KEY,
                new ArrayList<>(),
                new JsonListPreferenceAdapter(String.class)
        );
    }

    @VisibleForTesting
    SecureStoragePreferences() {
        // We have unit tests over this class so we can't really use  context. Those tests
        // already override every method in the class, so it's safe to have these values as null.
        sharedPreferences = null;
        auditPreference = null;
    }

    private void initSecureStorage() {
        if (!sharedPreferences.contains(MODE)) {
            sharedPreferences.edit().putString(MODE, getMode().name()).commit();
        }
    }

    /**
     * Fetch the IV for a given key, creating one if it doesn't exist.
     */
    public byte[] getAesIv(String key) {
        return getPersistentSecureRandomBytes(AES_IV_KEY_PREFIX + key, AES_IV_SIZE);
    }

    /**
     * Obtains a random sequence of bytes, this will be persisted under this module.
     */
    public synchronized byte[] getPersistentSecureRandomBytes(String key, int size) {
        if (sharedPreferences.contains(key)) {
            final byte[] iv = getBytes(key);

            // We've had a few InvalidKeyExceptions that might come from invalid IVs
            // So we check early and fail with some context
            if (iv.length != size) {
                throw new CryptographyException(
                        "IV for key " + key + " has size " + iv.length + " != " + size
                );
            }

            return iv;

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

        sharedPreferences.edit().putString(key, SerializationUtils.serializeBytes(bytes)).commit();
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
        sharedPreferences.edit().clear().commit();
        auditPreference.delete();
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
     * Check whether this module is currently storing data under the same mode as which is currently
     * operating or hasn't being initialized.
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
                .commit();
    }

    Set<String> getAllLabels() {
        final Set<String> result = new HashSet<>();
        for (final String label : sharedPreferences.getAll().keySet()) {
            if (!label.startsWith(AES_IV_KEY_PREFIX)) {
                result.add(label);
            }
        }

        return result;
    }

    Set<String> getAllIvLabels() {
        final Set<String> result = new HashSet<>();
        for (final String label : sharedPreferences.getAll().keySet()) {
            if (label.startsWith(AES_IV_KEY_PREFIX)) {
                result.add(label.replace(AES_IV_KEY_PREFIX, ""));
            }
        }

        return result;
    }

    void recordAuditTrail(final String operation, final String label) {
        try {
            @SuppressWarnings("ConstantConditions") // NonNull, default value for pref is empty list
            final List<String> trail = new ArrayList<>(auditPreference.get());

            final Instant instant = Instant.ofEpochMilli(System.currentTimeMillis());
            final ZonedDateTime now = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
            final String timestamp = now.format(Dates.ISO_DATE_TIME_WITH_MILLIS);
            trail.add(timestamp + " " + operation + " " + label);
            auditPreference.setNow(trail);
        } catch (final Exception e) {
            Timber.e("Failed to record audit trail (" + operation + " " + label + ")", e);
        }
    }

    List<String> getAuditTrail() {
        return auditPreference.get();
    }
}
