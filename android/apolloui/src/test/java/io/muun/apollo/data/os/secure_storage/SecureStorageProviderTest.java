package io.muun.apollo.data.os.secure_storage;

import io.muun.apollo.BaseTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SecureStorageProviderTest extends BaseTest {

    private static final byte[] SECRET_1 = "Secret 1".getBytes();
    private static final String SECRET_1_KEY = "secret_1";
    private static final byte[] SECRET_2 = "Secret 2 ! ~'. #".getBytes();
    private static final String SECRET_2_KEY = "secret_2";

    private FakePreferences preferences;
    private FakeKeyStore keyStore;

    private SecureStorageProvider secureStorage;

    @Parameter
    public SecureStorageMode mode;

    @Before
    public void setUp() {
        keyStore = new FakeKeyStore();

        preferences = new FakePreferences();

        secureStorage = new SecureStorageProvider(keyStore, preferences);
        preferences.setMode(mode);
    }

    @Parameters
    public static List<SecureStorageMode> modes() {
        return Arrays.asList(SecureStorageMode.J_MODE, SecureStorageMode.M_MODE);
    }

    @Test
    public void put_and_get_multiple_secrets() {
        putSecret(SECRET_1_KEY, SECRET_1);
        putSecret(SECRET_2_KEY, SECRET_2);
        assertThat(getSecret(SECRET_1_KEY))
                .isEqualTo(SECRET_1);
        assertThat(getSecret(SECRET_2_KEY))
                .isEqualTo(SECRET_2);
    }

    @Test
    public void delete_and_has_with_multiple_secrets() {
        preferences.setMode(SecureStorageMode.M_MODE);

        assertThat(secureStorage.has(SECRET_1_KEY)).isFalse();
        assertThat(secureStorage.has(SECRET_2_KEY)).isFalse();

        putSecret(SECRET_1_KEY, SECRET_1);

        assertThat(secureStorage.has(SECRET_1_KEY)).isTrue();
        assertThat(secureStorage.has(SECRET_2_KEY)).isFalse();

        putSecret(SECRET_2_KEY, SECRET_2);

        assertThat(secureStorage.has(SECRET_1_KEY)).isTrue();
        assertThat(secureStorage.has(SECRET_2_KEY)).isTrue();

        secureStorage.delete(SECRET_1_KEY);

        assertThat(secureStorage.has(SECRET_1_KEY)).isFalse();
        assertThat(secureStorage.has(SECRET_2_KEY)).isTrue();

        secureStorage.delete(SECRET_2_KEY);

        assertThat(secureStorage.has(SECRET_1_KEY)).isFalse();
        assertThat(secureStorage.has(SECRET_2_KEY)).isFalse();
    }

    @Test
    public void wipe_doesnt_leave_secrets() {
        putSecret(SECRET_1_KEY, SECRET_1);
        putSecret(SECRET_2_KEY, SECRET_2);

        secureStorage.wipe();

        assertThat(preferences.hasKey(SECRET_1_KEY)).isFalse();
        assertThat(preferences.hasKey(SECRET_2_KEY)).isFalse();
        assertThat(keyStore.hasKey(SECRET_1_KEY)).isFalse();
        assertThat(keyStore.hasKey(SECRET_2_KEY)).isFalse();

    }

    @Test(expected = SecureStorageProvider.SharedPreferencesCorruptedError.class)
    public void corrupt_preferences() {
        putSecret(SECRET_1_KEY, SECRET_1);
        preferences.wipe();

        getSecret(SECRET_1_KEY);
    }

    @Test(expected = SecureStorageProvider.KeyStoreCorruptedError.class)
    public void corrupt_keystore() {
        putSecret(SECRET_1_KEY, SECRET_1);
        keyStore.wipe();

        getSecret(SECRET_1_KEY);
    }

    @Test(expected = SecureStorageProvider.InconsistentModeError.class)
    public void inconsistent_mode() {
        preferences.setMode(SecureStorageMode.J_MODE);
        putSecret(SECRET_1_KEY, SECRET_1);

        preferences.setMode(SecureStorageMode.M_MODE);
        getSecret(SECRET_1_KEY);
    }

    @Test()
    public void corrupted_and_recover_after_wipe() {
        putSecret(SECRET_1_KEY, SECRET_1);
        keyStore.wipe();

        assertThatThrownBy(() -> getSecret(SECRET_1_KEY))
                .isInstanceOf(SecureStorageProvider.KeyStoreCorruptedError.class);

        secureStorage.wipe();

        putSecret(SECRET_1_KEY, SECRET_2);
        assertThat(getSecret(SECRET_1_KEY)).isEqualTo(SECRET_2);
    }

    private void putSecret(String key, byte[] secret) {
        secureStorage.put(key, secret);
    }

    private byte[] getSecret(String key) {
        return secureStorage.get(key);
    }
}
