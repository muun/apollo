package io.muun.apollo.data.secure_storage;

import io.muun.apollo.BaseTest;
import io.muun.apollo.data.os.authentication.PinManager;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class PinManagerTest extends BaseTest {

    private static final String PIN_1 = "1234";
    private static final String PIN_2 = "1111";

    private SecureStorageProvider secureStorageProvider;
    private PinManager pinManager;

    @Before
    public void setUp() {
        secureStorageProvider = new SecureStorageProvider(
                new FakeKeyStore(),
                new FakePreferences()
        );

        pinManager = new PinManager(secureStorageProvider);
    }

    @Test
    public void set_and_has() {
        assertThat(pinManager.hasPin()).isFalse();

        pinManager.storePin(PIN_1);

        assertThat(pinManager.hasPin()).isTrue();
    }

    @Test
    public void validate_pin() {
        pinManager.storePin(PIN_1);

        assertThat(pinManager.verifyPin(PIN_2)).isFalse();
        assertThat(pinManager.verifyPin(PIN_1)).isTrue();
    }

    @Test
    public void can_change_pin() {
        pinManager.storePin(PIN_1);
        pinManager.storePin(PIN_2);

        assertThat(pinManager.verifyPin(PIN_1)).isFalse();
        assertThat(pinManager.verifyPin(PIN_2)).isTrue();
    }
}
