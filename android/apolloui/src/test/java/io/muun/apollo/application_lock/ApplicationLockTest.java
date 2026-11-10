package io.muun.apollo.application_lock;


import io.muun.apollo.BaseTest;
import io.muun.apollo.data.os.authentication.PinManager;
import io.muun.apollo.data.os.secure_storage.FakeKeyStore;
import io.muun.apollo.data.os.secure_storage.FakePreferences;
import io.muun.apollo.data.os.secure_storage.SecureStorageMode;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.errors.IncorrectAttemptsUnavailableError;
import io.muun.apollo.domain.errors.SecureKeyValueStoreError;
import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.apollo.domain.errors.WeirdIncorrectAttemptsBugError;
import io.muun.apollo.domain.selector.ChallengePublicKeySelector;
import io.muun.apollo.domain.selector.LogoutOptionsSelector;
import io.muun.apollo.osmodules.securekv.SecureKeyValueStore;
import io.muun.common.utils.Encodings;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.Set;
import javax.crypto.BadPaddingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApplicationLockTest extends BaseTest {

    private static final String CORRECT_PIN = "1234";
    private static final String INCORRECT_PIN = "5678";

    /**
     * Mirrors the private constant in ApplicationLockManager. The recovery below keys off whether
     * the debug snapshot mentions this label, so the test has to name it.
     */
    private static final String KEY_INCORRECT_ATTEMPTS = "pin_incorrect_attempts";

    @Mock
    private PinManager pinManager;

    @Mock
    private ChallengePublicKeySelector challengePublicKeySel;

    @Mock
    private LogoutOptionsSelector logoutOptionsSelector;

    private ApplicationLockManager lockManager;

    @Before
    public void setUp() {
        final SecureStorageProvider secureStorageProvider = new SecureStorageProvider(
                new FakeKeyStore(),
                new FakePreferences()
        );

        lockManager = new ApplicationLockManager(
                pinManager,
                secureStorageProvider,
                new SecureKeyValueStoreRepository(new SecureKeyValueStore(secureStorageProvider)),
                challengePublicKeySel,
                logoutOptionsSelector
        );

        when(pinManager.verifyPin(CORRECT_PIN)).thenReturn(true);
        when(pinManager.verifyPin(INCORRECT_PIN)).thenReturn(false);

        doReturn(false).when(logoutOptionsSelector).isRecoverable();
    }

    @Test
    public void lockBeginsUnset() {
        assertThat(lockManager.isLockSet()).isFalse();
    }

    @Test
    public void isLockConfigured() {
        when(pinManager.hasPin()).thenReturn(false);
        assertThat(lockManager.isLockConfigured()).isFalse();

        when(pinManager.hasPin()).thenReturn(true);
        assertThat(lockManager.isLockConfigured()).isTrue();
    }

    @Test
    public void unlockWithCorrectPin() {
        lockManager.setLock();
        assertThat(lockManager.isLockSet()).isTrue();

        lockManager.tryUnlockWithPin(CORRECT_PIN);
        assertThat(lockManager.isLockSet()).isFalse();
    }

    @Test
    public void noUnlockWithIncorrectPin() {
        lockManager.setLock();
        assertThat(lockManager.isLockSet()).isTrue();

        lockManager.tryUnlockWithPin(INCORRECT_PIN);
        assertThat(lockManager.isLockSet()).isTrue();
    }

    @Test
    public void decrementsRemainingAttemptsWhenRecoverable() {
        doReturn(true).when(logoutOptionsSelector).isRecoverable();

        final int maxAttempts = lockManager.getMaxAttempts();
        assertThat(lockManager.getRemainingAttempts()).isEqualTo(maxAttempts);

        for (int i = 1; i <= maxAttempts; i++) {
            lockManager.tryUnlockWithPin(INCORRECT_PIN);
            assertThat(lockManager.getRemainingAttempts()).isEqualTo(maxAttempts - i);
        }
    }

    @Test(expected = WeirdIncorrectAttemptsBugError.class)
    public void errorOnZeroRemainingAttemptsWhenRecoverable() {
        doReturn(true).when(logoutOptionsSelector).isRecoverable();

        burnRemainingAttempts(lockManager.getMaxAttempts() + 1);
    }

    @Test
    public void doesNotDecrementAttemptsWhenUnrecoverable() {
        final int maxAttempts = lockManager.getMaxAttempts();

        assertThat(lockManager.getRemainingAttempts()).isEqualTo(maxAttempts);
        lockManager.tryUnlockWithPin(INCORRECT_PIN);
        assertThat(lockManager.getRemainingAttempts()).isEqualTo(maxAttempts);
    }

    @Test
    @Ignore("flaky")
    public void autoSetLock() throws InterruptedException {
        lockManager.autoSetLockAfterDelay(10);

        assertThat(lockManager.isLockSet()).isFalse();
        Thread.sleep(20);
        assertThat(lockManager.isLockSet()).isTrue();
    }

    @Test
    @Ignore("flaky")
    public void cancelAutoSetLock() throws InterruptedException {
        lockManager.autoSetLockAfterDelay(10);

        assertThat(lockManager.isLockSet()).isFalse();
        Thread.sleep(5);
        lockManager.cancelAutoSetLocked();
        Thread.sleep(15);
        assertThat(lockManager.isLockSet()).isFalse();
    }

    @Test
    public void resetAttemptsAfterUnlock() {
        lockManager.tryUnlockWithPin(INCORRECT_PIN);

        burnRemainingAttempts(1);
        lockManager.tryUnlockWithPin(CORRECT_PIN);
        assertThat(lockManager.getRemainingAttempts()).isEqualTo(lockManager.getMaxAttempts());


        // TODO attempts should be reset on biometric successful unlock
    }

    @Test
    public void resetsAttemptsWhenNoCounterWasEverStored() {
        // Given: a fresh install. The counter is absent, which is not a failure, so it is
        // seeded rather than surfaced.
        final SecureStorageProvider provider = providerFailingOnGet(
                new SecureStorageProvider.SecureStorageNoSuchElementError(
                        KEY_INCORRECT_ATTEMPTS,
                        debugSnapshot(Collections.emptySet())
                )
        );
        final ApplicationLockManager manager = lockManagerOver(provider);

        assertThat(manager.getRemainingAttempts()).isEqualTo(manager.getMaxAttempts());
        verify(provider).put(eq(KEY_INCORRECT_ATTEMPTS), eq(Encodings.intToBytes(0)));
    }

    @Test
    public void resetsAttemptsWhenTheKeystoreKeyIsGone() {
        // Given: the ciphertext is still in prefs (the snapshot lists its IV) but the keystore
        // key was wiped. The counter is unreadable and unrecoverable, so it's reset.
        final SecureStorageProvider provider = providerFailingOnGet(
                keyStoreCorrupted(Collections.singleton(KEY_INCORRECT_ATTEMPTS))
        );
        final ApplicationLockManager manager = lockManagerOver(provider);

        assertThat(manager.getRemainingAttempts()).isEqualTo(manager.getMaxAttempts());
        verify(provider).put(eq(KEY_INCORRECT_ATTEMPTS), eq(Encodings.intToBytes(0)));
    }

    @Test
    public void failsWhenTheKeystoreKeyIsGoneAndNoIvWasLeftBehind() {
        // Given: the same missing keystore key, but nothing in prefs points at this label. The
        // workaround doesn't apply and the error has to reach the caller.
        final SecureStorageProvider provider =
                providerFailingOnGet(keyStoreCorrupted(Collections.emptySet()));
        final ApplicationLockManager manager = lockManagerOver(provider);

        assertThatThrownBy(manager::getRemainingAttempts)
                .isInstanceOf(IncorrectAttemptsUnavailableError.class)
                .hasCauseInstanceOf(SecureKeyValueStoreError.KeystoreKeyMissing.class);

        verify(provider, never()).put(anyString(), any());
    }

    @Test
    public void readsZeroAttemptsOnABadPaddingFailureWithoutRewriting() {
        // Given: the value didn't decrypt, but the key material is still there. Rewriting would
        // destroy a counter that may yet be readable, so this one only degrades to zero.
        final SecureStorageProvider provider = providerFailingOnGet(new SecureStorageError(
                new BadPaddingException("boom"),
                debugSnapshot(Collections.emptySet())
        ));
        final ApplicationLockManager manager = lockManagerOver(provider);

        assertThat(manager.getRemainingAttempts()).isEqualTo(manager.getMaxAttempts());
        verify(provider, never()).put(anyString(), any());
    }

    @Test
    public void reportsAFailedCounterWriteAsACounterFailure() {
        // Given: a keystore that can't encrypt, so the counter can be read but never written
        // back. The lock screen tells this apart from an unreadable pin by the type alone, so
        // the write has to say which of the two it was.
        final SecureStorageProvider provider = new SecureStorageProvider(
                new FakeKeyStore() {
                    @Override
                    public byte[] encryptData(byte[] input, String alias, byte[] iv) {
                        throw new RuntimeException("boom");
                    }
                },
                new FakePreferences()
        );
        final ApplicationLockManager manager = lockManagerOver(provider);

        assertThatThrownBy(() -> manager.tryUnlockWithPin(CORRECT_PIN))
                .isInstanceOf(IncorrectAttemptsUnavailableError.class)
                .hasCauseInstanceOf(SecureKeyValueStoreError.StorageFailed.class);
    }

    private ApplicationLockManager lockManagerOver(SecureStorageProvider provider) {
        return new ApplicationLockManager(
                pinManager,
                provider,
                new SecureKeyValueStoreRepository(new SecureKeyValueStore(provider)),
                challengePublicKeySel,
                logoutOptionsSelector
        );
    }

    private SecureStorageProvider providerFailingOnGet(Throwable error) {
        final SecureStorageProvider provider = mock(SecureStorageProvider.class);
        doThrow(error).when(provider).get(anyString());
        return provider;
    }

    private SecureStorageProvider.KeyStoreCorruptedError keyStoreCorrupted(Set<String> ivLabels) {
        return new SecureStorageProvider.KeyStoreCorruptedError(
                KEY_INCORRECT_ATTEMPTS,
                debugSnapshot(ivLabels)
        );
    }

    private SecureStorageProvider.DebugSnapshot debugSnapshot(Set<String> ivLabels) {
        return new SecureStorageProvider.DebugSnapshot(
                SecureStorageMode.M_MODE,
                true,
                Collections.emptySet(),
                ivLabels,
                Collections.emptySet(),
                null,
                Collections.emptyList()
        );
    }

    private void burnRemainingAttempts(int amount) {
        for (int i = 0; i < amount; i++) {
            lockManager.tryUnlockWithPin(INCORRECT_PIN);
        }
    }
}
