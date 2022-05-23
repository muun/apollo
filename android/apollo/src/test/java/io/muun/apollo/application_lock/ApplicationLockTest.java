package io.muun.apollo.application_lock;


import io.muun.apollo.BaseTest;
import io.muun.apollo.data.os.authentication.PinManager;
import io.muun.apollo.data.os.secure_storage.FakeKeyStore;
import io.muun.apollo.data.os.secure_storage.FakePreferences;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.selector.ChallengePublicKeySelector;

import android.content.Context;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class ApplicationLockTest extends BaseTest {

    private static final String CORRECT_PIN = "1234";
    private static final String INCORRECT_PIN = "5678";

    @Mock
    private PinManager pinManager;

    @Mock
    private ChallengePublicKeySelector challengePublicKeySel;

    @Mock
    private Context context; // Not really used in this tests

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
                challengePublicKeySel,
                context
        );

        when(pinManager.verifyPin(CORRECT_PIN)).thenReturn(true);
        when(pinManager.verifyPin(INCORRECT_PIN)).thenReturn(false);

        doReturn(false).when(challengePublicKeySel).exists(any());
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
        doReturn(true).when(challengePublicKeySel).exists(any());

        final int maxAttempts = lockManager.getMaxAttempts();
        assertThat(lockManager.getRemainingAttempts()).isEqualTo(maxAttempts);

        for (int i = 1; i <= maxAttempts; i++) {
            lockManager.tryUnlockWithPin(INCORRECT_PIN);
            assertThat(lockManager.getRemainingAttempts()).isEqualTo(maxAttempts - i);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void errorOnZeroRemainingAttemptsWhenRecoverable() {
        doReturn(true).when(challengePublicKeySel).exists(any());

        burnRemainingAttempts(lockManager.getMaxAttempts() + 1);
    }

    @Test
    public void doesNotDecrementAttemptsWhenUnrecoverable() {
        doReturn(false).when(challengePublicKeySel).exists(any());

        final int maxAttempts = lockManager.getMaxAttempts();

        assertThat(lockManager.getRemainingAttempts()).isEqualTo(maxAttempts);
        lockManager.tryUnlockWithPin(INCORRECT_PIN);
        assertThat(lockManager.getRemainingAttempts()).isEqualTo(maxAttempts);
    }

    @Test
    public void unlockWithAnyFingerprint() {
        lockManager.tryUnlockWithFingerprint();
        assertThat(lockManager.isLockSet()).isFalse();
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

        burnRemainingAttempts(1);
        lockManager.tryUnlockWithFingerprint();
        assertThat(lockManager.getRemainingAttempts()).isEqualTo(lockManager.getMaxAttempts());
    }

    private void burnRemainingAttempts(int amount) {
        for (int i = 0; i < amount; i++) {
            lockManager.tryUnlockWithPin(INCORRECT_PIN);
        }
    }
}
