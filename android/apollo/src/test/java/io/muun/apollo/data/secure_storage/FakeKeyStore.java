package io.muun.apollo.data.secure_storage;

import io.muun.apollo.data.os.secure_storage.KeyStoreProvider;

import android.content.Context;
import androidx.core.util.Pair;

import java.util.Arrays;
import java.util.HashMap;

import static org.mockito.Mockito.mock;

public class FakeKeyStore extends KeyStoreProvider {
    private static final byte[] ZERO_BYTES = "".getBytes();
    private final HashMap<String, Pair<byte[], byte[]>> map = new HashMap<>();

    public FakeKeyStore() {
        super(mock(Context.class));
    }

    @Override
    public byte[] encryptData(byte[] input, String alias, byte[] iv) {
        map.put(alias, Pair.create(input.clone(), iv));

        return ZERO_BYTES;
    }

    @Override
    public byte[] decryptData(byte[] input, String alias, byte[] iv) {
        final Pair<byte[], byte[]> pair = map.get(alias);

        if (!Arrays.equals(pair.second, iv)) {
            throw new RuntimeException("IV doesn't match !");
        }

        return pair.first;
    }

    @Override
    public void deleteEntry(String keyAlias) {
        map.remove(keyAlias);
    }

    @Override
    public boolean hasKey(String keyAlias) {
        return map.containsKey(keyAlias);
    }

    @Override
    public void wipe() {
        map.clear();
    }
}
