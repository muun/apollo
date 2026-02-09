package io.muun.apollo.presentation.ui.activity.extension;

import io.muun.apollo.R;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.analytics.Analytics;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.errors.WeirdIncorrectAttemptsBugError;
import io.muun.apollo.domain.model.BiometricAuthenticationErrorReason;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.apollo.presentation.app.Navigator;
import io.muun.apollo.presentation.biometrics.BiometricsController;
import io.muun.apollo.presentation.ui.base.ActivityExtension;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.view.MuunLockOverlay;
import io.muun.common.exception.MissingCaseError;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.annotation.StringRes;
import icepick.State;
import kotlin.Unit;
import rx.Single;
import rx.exceptions.OnErrorNotImplementedException;
import timber.log.Timber;

import java.util.Set;
import javax.inject.Inject;

@PerActivity
public class ApplicationLockExtension extends ActivityExtension {

    private static final int AFTER_SUCCESS_DELAY_MS = 200;

    private static final int CUSTOM_SOFT_INPUT_DISABLED = (
            // NOTE: always keep ADJUST_RESIZE, or it will never work again, even if re-set later.
            // Only remove if willing to experiment for hours and then `reset --hard`.
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    );

    private final Navigator navigator;
    private final ApplicationLockManager lockManager;
    private final ExecutionTransformerFactory executionTransformerFactory;

    private final Analytics analytics;

    private final BiometricsController biometricsController;

    private MuunLockOverlay lockOverlay;

    @State
    boolean requireUnlock = true;

    @State
    int softInputModeBeforeLock;

    /**
     * Public constructor for injection.
     */
    @Inject
    public ApplicationLockExtension(Navigator navigator,
                                    ApplicationLockManager lockManager,
                                    ExecutionTransformerFactory executionTransformerFactory,
                                    Analytics analytics,
                                    BiometricsController biometricsController) {
        this.navigator = navigator;
        this.lockManager = lockManager;
        this.executionTransformerFactory = executionTransformerFactory;
        this.analytics = analytics;
        this.biometricsController = biometricsController;
    }

    @Override
    public void onResume() {
        lockManager.cancelAutoSetLocked();

        if (requireUnlock && lockManager.isLockConfigured() && lockManager.isLockSet()) {
            showLockOverlay();
        } else {
            hideLockOverlay();
        }
    }

    public boolean isShowingLockOverlay() {
        return lockOverlay != null;
    }

    public void setRequireUnlock(boolean requireUnlock) {
        this.requireUnlock = requireUnlock;
    }

    private void showLockOverlay() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#showLockOverlay");

        if (lockOverlay == null) {
            lockOverlay = new MuunLockOverlay(getActivity());

            lockOverlay.setPinLength(lockManager.getPinLength());
            lockOverlay.setListener(new BoundLockOverlayListener());

            lockOverlay.attachToRoot();
            disableSoftInput();

            analytics.report(new AnalyticsEvent.S_PIN_LOCKED(getActivity()));
        }

        updateLockOverlayAttempts();
    }

    private void hideLockOverlay() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#hideLockOverlay");
        if (lockOverlay != null) {
            lockOverlay.setListener(null);
            lockOverlay.detachFromRoot();
            lockOverlay = null;

            enableSoftInput();
        }
    }

    private void disableSoftInput() {
        softInputModeBeforeLock = getSoftInputMode();
        setSoftInputMode(CUSTOM_SOFT_INPUT_DISABLED);
    }

    private void enableSoftInput() {
        if (getSoftInputMode() != CUSTOM_SOFT_INPUT_DISABLED) {
            return; // somebody else changed this. Let's assume they knew what they were doing.
        }

        setSoftInputMode(softInputModeBeforeLock);

        final View currentFocus = getActivity().getCurrentFocus();

        if (currentFocus instanceof EditText) {
            // EditText also covers TextInputEditText, used by MuunTextInput. Is this enough?
            showSoftInput(currentFocus);
        }
    }

    private void setSoftInputMode(int mode) {
        getActivity().getWindow().setSoftInputMode(mode);
    }

    private int getSoftInputMode() {
        return getActivity().getWindow().getAttributes().softInputMode;
    }

    private void showSoftInput(View target) {
        final InputMethodManager imm = (InputMethodManager) getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
    }

    private void updateLockOverlayAttempts() {
        Timber.i("ApplicationLockExtension: updateLockOverlayAttempts");

        lockOverlay.setRemainingAttempts(
                lockManager.getRemainingAttempts(),
                lockManager.getMaxAttempts()
        );
    }

    private void onUnlockAttemptFailure() {
        Timber.i("ApplicationLockExtension: onUnlockAttemptFailure");

        if (lockOverlay != null) { // avoid race conditions
            updateLockOverlayAttempts();

            analytics.report(new AnalyticsEvent.E_PIN(AnalyticsEvent.PIN_TYPE.INCORRECT));

            lockOverlay.reportError(() -> {
                if (lockManager.getRemainingAttempts() == 0) {
                    navigator.navigateToSecurityLogout(getActivity());
                }
            });
        }
    }

    private void onUnlockAttemptSuccess() {
        Timber.i("ApplicationLockExtension: onUnlockAttemptSuccess");

        if (lockOverlay != null) { // avoid race conditions
            lockOverlay.reportSuccess();
            analytics.report(new AnalyticsEvent.E_PIN(AnalyticsEvent.PIN_TYPE.CORRECT));
            new Handler().postDelayed(this::hideLockOverlay, AFTER_SUCCESS_DELAY_MS);
        }
    }

    private class BoundLockOverlayListener implements MuunLockOverlay.LockOverlayListener {

        @Override
        public void onPinEntered(String pin) {
            Timber.i("ApplicationLockExtension: onPinEntered. " + this);

            Single.fromCallable(() -> lockManager.tryUnlockWithPin(pin))
                    .compose(executionTransformerFactory.getSingleAsyncExecutor())
                    .subscribe(isUnlocked -> {
                        if (isUnlocked) {
                            onUnlockAttemptSuccess();

                        } else {
                            onUnlockAttemptFailure();
                        }
                    }, throwable -> {
                        lockOverlay.reportError(null);

                        // Avoid crashes due to keystore's weird bugs. If it's a secure storage
                        // error, catch it, otherwise re-throw it
                        if (ExtensionsKt.isInstanceOrIsCausedBySecureStorageError(throwable)) {
                            Timber.e(throwable); // Probably redundant, should already be logged

                        } else if (throwable instanceof WeirdIncorrectAttemptsBugError) {
                            // Attempt to log/track weird error we've seen in prd. Handle error will
                            // show error report dialog and hopefully users will send it to us.
                            Timber.e(throwable);
                            ((BaseActivity) getActivity()).getPresenter().handleError(throwable);

                        } else {
                            // IDKW but we can't throw other error than this one, go figure
                            throw new OnErrorNotImplementedException(throwable);
                        }
                    });
        }

        @Override
        public void onUnlockUsingBiometrics() {
            Timber.i("ApplicationLockExtension: onUnlockUsingBiometrics. " + this);

            biometricsController.authenticate(getActivity(),
                    getActivity().getString(R.string.biometrics_unlock_title),
                    getActivity().getString(R.string.biometrics_unlock_subtitle),
                    () -> {
                        biometricsController.setUserOptInBiometrics(true);
                        lockManager.unlockWithBiometrics();
                        onUnlockAttemptSuccess();
                        return Unit.INSTANCE;
                    },
                    (error) -> {
                        // BiometricAuthenticationErrorReason.GENERAL wraps a lot of OS errors that
                        // doesn't have a clear reason to show to the user, so in this case we
                        // decided to fail silently.
                        if (!Set.of(
                                BiometricAuthenticationErrorReason.LOCKOUT,
                                BiometricAuthenticationErrorReason.LOCKOUT_PERMANENT
                        ).contains(error.getReason())) {
                            return Unit.INSTANCE;
                        }

                        final @StringRes int errorStringRes = switch (error.getReason()) {
                            case LOCKOUT -> R.string.biometrics_unlock_error_lockout_desc;
                            case LOCKOUT_PERMANENT ->
                                    R.string.biometrics_unlock_error_lockout_permanent_desc;
                            default -> throw new MissingCaseError(error.getReason());
                        };

                        ((BaseActivity) getActivity()).showErrorDialog(errorStringRes);
                        return Unit.INSTANCE;
                    }
            );
        }
    }
}
