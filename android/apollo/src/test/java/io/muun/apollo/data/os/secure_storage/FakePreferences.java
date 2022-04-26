package io.muun.apollo.data.os.secure_storage;

import io.muun.common.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FakePreferences extends SecureStoragePreferences {
    private final HashMap<String, byte[]> map = new HashMap<>();
    private SecureStorageMode mode = SecureStorageMode.M_MODE;
    private SecureStorageMode lastModeUsedInSave;

    public FakePreferences() {
        super();
    }

    public byte[] getAesIv(String key) {
        return getPersistentSecureRandomBytes("IV_" + key, 16);
    }

    public byte[] getPersistentSecureRandomBytes(String key, int size) {
        if (map.containsKey(key)) {
            return map.get(key);

        } else {
            final byte[] bytes = RandomGenerator.getBytes(size);
            map.put(key, bytes);
            return bytes;
        }
    }

    public void saveBytes(byte[] bytes, String key) {
        this.lastModeUsedInSave = mode;

        map.put(key, bytes);
    }

    public byte[] getBytes(String key) {
        return map.get(key);
    }

    public boolean hasKey(String key) {
        return map.containsKey(key);
    }

    public void wipe() {
        map.clear();
    }

    public boolean isCompatibleFormat() {
        return lastModeUsedInSave == null || lastModeUsedInSave == mode;
    }

    @Override
    public SecureStorageMode getMode() {
        return mode;
    }

    public void delete(String key) {
        map.remove(key);
    }

    public void setMode(SecureStorageMode mode) {
        this.mode = mode;
    }

    @Override
    List<String> getAuditTrail() {
        return new ArrayList<>();
    }

    @Override
    Set<String> getAllIvLabels() {
        return new HashSet<>();
    }

    @Override
    Set<String> getAllLabels() {
        return new HashSet<>();
    }

    @Override
    void recordAuditTrail(final String operation, final String label) {
    }
}
