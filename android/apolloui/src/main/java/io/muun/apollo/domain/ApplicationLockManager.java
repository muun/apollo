package io.muun.apollo.domain;


import io.muun.apollo.data.os.authentication.PinManager;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.apollo.domain.errors.WeirdIncorrectAttemptsBugError;
import io.muun.apollo.domain.selector.ChallengePublicKeySelector;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Preconditions;

import androidx.annotation.VisibleForTesting;
import rx.Observable;
import rx.Subscription;
import timber.log.Timber;

import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ApplicationLockManager {

    public interface UnlockListener {
        /**
         * Called when lock screen has successfully been unlocked.
         */
        void onUnlock();
    }

    private static final int MAX_ATTEMPTS = 3;
    private static final int AUTO_SET_LOCK_DELAY_SECONDS = 10;
    private static final String KEY_INCORRECT_ATTEMPTS = "pin_incorrect_attempts";

    private boolean isLocked;
    private Subscription autoSetTimer;
    private UnlockListener unlockListener;

    private final PinManager pinManager;
    private final SecureStorageProvider secureStorageProvider;
    private final ChallengePublicKeySelector challengePublicKeySel;

    /**
     * Constructor.
     */
    @Inject
    public ApplicationLockManager(
            PinManager pinManager,
            SecureStorageProvider secureStorageProvider,
            ChallengePublicKeySelector challengePublicKeySel
    ) {

        this.pinManager = pinManager;
        this.secureStorageProvider = secureStorageProvider;
        this.challengePublicKeySel = challengePublicKeySel;
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

        Timber.i("ApplicationLockManager#tryUnlockWithPin");

        try {
            Preconditions.checkPositive(getRemainingAttempts());
        } catch (IllegalArgumentException e) {
            throw new WeirdIncorrectAttemptsBugError(
                    getRemainingAttempts(),
                    getMaxAttempts(),
                    secureStorageProvider.debugSnapshot()
            );
        }

        final boolean verified = pinManager.verifyPin(pin);

        if (verified) {
            unsetLock();
            resetRemainingAttempts();

        } else if (challengePublicKeySel.existsAnyType()) {
            // NOTE: this won't count failures for unrecoverable users.
            decrementRemainingAttempts();
        }

        Timber.i("ApplicationLockManager#verified: " + verified);

        return verified;
    }

    public synchronized void unlockWithBiometrics() {
        unsetLock();
        resetRemainingAttempts();
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

    public int getPinLength() {
        return pinManager.getPinLength();
    }

    public synchronized int getRemainingAttempts() {
        return MAX_ATTEMPTS - fetchIncorrectAttempts();
    }

    public void setUnlockListener(UnlockListener listener) {
        unlockListener = listener;
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
        if (unlockListener != null) {
            unlockListener.onUnlock();
            unlockListener = null;
        }
    }

    private synchronized void decrementRemainingAttempts() {

        Timber.i("ApplicationLockManager#decrementRemainingAttempts");

        final int incorrectAttempts = Math.min(
                fetchIncorrectAttempts() + 1,
                getMaxAttempts()
        );

        Timber.i(
                "ApplicationLockManager#storeIncorrectAttempts: " + incorrectAttempts
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

        secureStorageProvider.put(KEY_INCORRECT_ATTEMPTS, Encodings.intToBytes(incorrectAttempts));
    }

    private synchronized int fetchIncorrectAttempts() {
        Timber.i("ApplicationLockManager#fetchIncorrectAttempts");

        try {
            return Encodings.bytesToInt(secureStorageProvider.get(KEY_INCORRECT_ATTEMPTS));

        } catch (SecureStorageProvider.SecureStorageNoSuchElementError error) {
            // Yeah, we shouldn't use exceptions for normal control flow, but for now...
            // TODO: this whole class needs some serious refactoring
            Timber.i("Resetting incorrect attempts to 0");
            storeIncorrectAttempts(0);
            return 0;

        } catch (SecureStorageProvider.KeyStoreCorruptedError error) {
            // Note: KeyStoreCorruptedError is subclass of SecureStorageError so this catch needs
            // to be above the more general one below

            // KeyStoreCorruptedError implies that data for this key is stored in preference but
            // somehow, due to a bizarre Android Keystore bug, Keystore Key for this key/label got
            // wiped. We proceed to check if the associated IV is present in preferences to conclude
            // that this is effectively the bug case scenario and reset the incorrect attempts to
            // force SecureStorageProvider to resolve this data inconsistency.
            final String ivsInPrefs = (String) error.getMetadata().get("labelsWithIvInPrefs");

            Timber.i("KeyStoreCorruptedError in fetchIncorrectAttempts");

            if (ivsInPrefs != null && ivsInPrefs.contains(KEY_INCORRECT_ATTEMPTS)) {

                Timber.e(error, "WORKAROUND for KeyStoreCorruptedError in fetchIncorrectAttempts");
                storeIncorrectAttempts(0);
                return 0;

            } else {
                Timber.e(error, "Unsolvable KeyStoreCorruptedError in fetchIncorrectAttempts");
                throw error;
            }

        } catch (SecureStorageError error) {
            if (ExtensionsKt.isCauseByBadPaddingException(error)) {
                // If this error is caused by a BadPadding Exception coming from the Android
                // Keystore, we try continue execution hoping this is the only piece of data
                // affected by this data corruption.
                error.addMetadata("hasBackup", challengePublicKeySel.existsAnyType());
                Timber.i("bad_padding_exception_workaround");
                Timber.e(error, "WORKAROUND for BadPaddingException in fetchIncorrectAttempts");
                return 0;

            } else {
                throw error;
            }

        }
    }
}
