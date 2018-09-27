package io.muun.apollo.domain;


import io.muun.apollo.data.os.authentication.PinManager;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Preconditions;

import android.support.annotation.VisibleForTesting;
import rx.Observable;
import rx.Subscription;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ApplicationLockManager {

    private static final int MAX_ATTEMPTS = 3;
    private static final int AUTO_SET_LOCK_DELAY_SECONDS = 10;
    private static final String KEY_INCORRECT_ATTEMPTS = "pin_incorrect_attempts";

    private boolean isLocked;
    private Subscription autoSetTimer;

    private final PinManager pinManager;
    private final SecureStorageProvider secureStorageProvider;

    /**
     * Constructor.
     */
    @Inject
    public ApplicationLockManager(PinManager pinManager,
                                  SecureStorageProvider secureStorageProvider) {

        this.pinManager = pinManager;
        this.secureStorageProvider = secureStorageProvider;
    }

    public synchronized boolean isLockConfigured() {
        return pinManager.hasPin();
    }

    public synchronized boolean isLockSet() {
        return isLocked;
    }

    /**
     * Attempt to unset the lock with a PIN.
     */
    public synchronized boolean tryUnlockWithPin(String pin) {
        Preconditions.checkPositive(getRemainingAttempts());

        final boolean verified = pinManager.verifyPin(pin);

        if (verified) {
            unsetLock();
            resetRemainingAttempts();
        } else {
            decrementRemainingAttempts();
        }

        return verified;
    }

    /**
     * Attempt to unset the lock with a fingerprint.
     */
    public synchronized void tryUnlockWithFingerprint() {
        Preconditions.checkPositive(getRemainingAttempts());

        // Fingerprint should already be validated by the OS. We have nothing to check.
        resetRemainingAttempts();
        unsetLock();
    }

    /**
     * Automatically set the application locked state after a delay. Can be canceled (see below).
     */
    public synchronized void autoSetLockAfterDelay() {
        autoSetLockAfterDelay(AUTO_SET_LOCK_DELAY_SECONDS * 1000);
    }

    /**
     * Do not call. You should use the no-argument version. Visible for testing.
     */
    @VisibleForTesting
    public synchronized void autoSetLockAfterDelay(int milliseconds) {
        cancelAutoSetLocked();

        autoSetTimer = Observable
                .timer(milliseconds, TimeUnit.MILLISECONDS)
                .subscribe(ignored -> setLock());
    }

    /**
     * Cancel a delayed automatic lock.
     */
    public synchronized void cancelAutoSetLocked() {
        if (autoSetTimer != null) {
            autoSetTimer.unsubscribe();
            autoSetTimer = null;
        }
    }

    public int getMaxAttempts() {
        return MAX_ATTEMPTS;
    }

    public synchronized int getRemainingAttempts() {
        return MAX_ATTEMPTS - fetchIncorrectAttempts();
    }

    /**
     * Set the application lock. Note this is public, unlike `unsetLock()`.
     */
    public synchronized void setLock() {
        this.isLocked = true;
        cancelAutoSetLocked();
    }

    /**
     * Unset the application lock. Note this is private.
     */
    private synchronized void unsetLock() {
        this.isLocked = false;
        cancelAutoSetLocked();
    }

    private synchronized void decrementRemainingAttempts() {
        final int incorrectAttempts = Math.min(
                fetchIncorrectAttempts() + 1,
                getMaxAttempts()
        );

        storeIncorrectAttempts(incorrectAttempts);
    }

    private synchronized void resetRemainingAttempts() {
        storeIncorrectAttempts(0);
    }

    private synchronized void storeIncorrectAttempts(int incorrectAttempts) {
        // NOTE: we store *incorrect* and not *remaining* attempts, in case the MAX_ATTEMPTS
        // constant is modified in an application update. This is a bit of an overkill, but it
        // doesn't add that much complexity.

        final byte[] incorrectAttemptBytes = Encodings.intToBytes(incorrectAttempts);

        secureStorageProvider
                .put(KEY_INCORRECT_ATTEMPTS, incorrectAttemptBytes)
                .toBlocking()
                .first();
    }

    private synchronized int fetchIncorrectAttempts() {
        try {
            final byte[] incorrectAttemptBytes = secureStorageProvider
                    .get(KEY_INCORRECT_ATTEMPTS)
                    .toBlocking()
                    .first();

            return Encodings.bytesToInt(incorrectAttemptBytes);

        } catch (NoSuchElementException error) {
            storeIncorrectAttempts(0);
            return 0;
        }
    }
}
