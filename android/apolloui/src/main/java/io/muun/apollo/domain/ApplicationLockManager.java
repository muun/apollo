package io.muun.apollo.domain;


import io.muun.apollo.data.os.authentication.PinManager;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository;
import io.muun.apollo.domain.errors.IncorrectAttemptsUnavailableError;
import io.muun.apollo.domain.errors.SecureKeyValueStoreError;
import io.muun.apollo.domain.errors.WeirdIncorrectAttemptsBugError;
import io.muun.apollo.domain.selector.ChallengePublicKeySelector;
import io.muun.apollo.domain.selector.LogoutOptionsSelector;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Preconditions;

import androidx.annotation.VisibleForTesting;
import kotlin.Unit;
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
    private final SecureKeyValueStoreRepository secureKeyValueStoreRepository;
    private final ChallengePublicKeySelector challengePublicKeySel;
    private final LogoutOptionsSelector logoutOptionsSelector;

    /**
     * Constructor.
     */
    @Inject
    public ApplicationLockManager(
            PinManager pinManager,
            SecureStorageProvider secureStorageProvider,
            SecureKeyValueStoreRepository secureKeyValueStoreRepository,
            ChallengePublicKeySelector challengePublicKeySel,
            LogoutOptionsSelector logoutOptionsSelector
    ) {

        this.pinManager = pinManager;
        this.secureStorageProvider = secureStorageProvider;
        this.secureKeyValueStoreRepository = secureKeyValueStoreRepository;
        this.challengePublicKeySel = challengePublicKeySel;
        this.logoutOptionsSelector = logoutOptionsSelector;
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

        } else if (logoutOptionsSelector.isRecoverable()) {
            // NOTE: this won't count failures for unrecoverable users.
            decrementRemainingAttempts();
        }

        Timber.i("ApplicationLockManager#verified: %s", verified);

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

        Timber.i("ApplicationLockManager#storeIncorrectAttempts: %s", incorrectAttempts);
        storeIncorrectAttempts(incorrectAttempts);
    }

    private synchronized void resetRemainingAttempts() {
        storeIncorrectAttempts(0);
    }

    private synchronized void storeIncorrectAttempts(int incorrectAttempts) {
        // NOTE: we store *incorrect* and not *remaining* attempts, in case the MAX_ATTEMPTS
        // constant is modified in an application update. This is a bit of an overkill, but it
        // doesn't add that much complexity.

        try {
            secureKeyValueStoreRepository.put(
                    KEY_INCORRECT_ATTEMPTS,
                    Encodings.intToBytes(incorrectAttempts)
            );
        } catch (SecureKeyValueStoreError error) {
            // Reported as a counter failure, not a pin one: the lock screen tells them apart by
            // type, and securekv raises the same subtypes for both.
            throw new IncorrectAttemptsUnavailableError(error);
        }
    }

    /**
     * Read the counter, reporting a failure as a counter failure and not as a pin one.
     *
     * <p>Wrapping around the read rather than at each throw site covers the writes it makes on
     * its way out: those run inside its catch clauses, where a sibling catch can't reach them.
     */
    private synchronized int fetchIncorrectAttempts() {
        try {
            return readIncorrectAttempts();
        } catch (SecureKeyValueStoreError error) {
            throw new IncorrectAttemptsUnavailableError(error);
        }
    }

    private synchronized int readIncorrectAttempts() {
        Timber.i("ApplicationLockManager#fetchIncorrectAttempts");

        // The counter is not secret material: the Secret buys nothing here beyond going through
        // the one door into secure storage.
        final int[] incorrectAttempts = new int[1];

        try {
            secureKeyValueStoreRepository.get(KEY_INCORRECT_ATTEMPTS).withSecret(bytes -> {
                incorrectAttempts[0] = Encodings.bytesToInt(bytes);
                return Unit.INSTANCE;
            });

            return incorrectAttempts[0];

        } catch (SecureKeyValueStoreError.NotFound error) {
            // Yeah, we shouldn't use exceptions for normal control flow, but for now...
            // TODO: this whole class needs some serious refactoring
            Timber.i("Resetting incorrect attempts to 0");
            storeIncorrectAttempts(0);
            return 0;

        } catch (SecureKeyValueStoreError.KeystoreKeyMissing error) {
            // Note: KeystoreKeyMissing is a subclass of DecryptionFailed so this catch needs
            // to be above the more general one below

            // KeystoreKeyMissing implies that data for this key is stored in preference but
            // somehow, due to a bizarre Android Keystore bug, Keystore Key for this key/label got
            // wiped. We proceed to check if the associated IV is present in preferences to conclude
            // that this is effectively the bug case scenario and reset the incorrect attempts to
            // force the secure storage to resolve this data inconsistency.
            final String ivsInPrefs = (String) error.getMetadata().get("labelsWithIvInPrefs");

            Timber.i("KeystoreKeyMissing in fetchIncorrectAttempts");

            if (ivsInPrefs != null && ivsInPrefs.contains(KEY_INCORRECT_ATTEMPTS)) {

                Timber.e(error, "WORKAROUND for KeystoreKeyMissing in fetchIncorrectAttempts");
                storeIncorrectAttempts(0);
                return 0;

            } else {
                Timber.e(error, "Unsolvable KeystoreKeyMissing in fetchIncorrectAttempts");
                throw error;
            }

        } catch (SecureKeyValueStoreError error) {
            if (ExtensionsKt.isCauseByBadPaddingException(error)) {
                // If this error is caused by a BadPadding Exception coming from the Android
                // Keystore, we try continue execution hoping this is the only piece of data
                // affected by this data corruption.
                error.addMetadata("hasChallengePublicKeys", challengePublicKeySel.existsAnyType());
                error.addMetadata("userIsRecoverable", logoutOptionsSelector.isRecoverable());

                Timber.i("bad_padding_exception_workaround");
                Timber.e(error, "WORKAROUND for BadPaddingException in fetchIncorrectAttempts");
                return 0;

            } else {
                throw error;
            }

        }
    }
}
