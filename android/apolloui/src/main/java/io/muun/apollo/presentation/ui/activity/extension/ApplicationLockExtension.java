package io.muun.apollo.presentation.ui.activity.extension;

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.presentation.analytics.Analytics;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.app.Navigator;
import io.muun.apollo.presentation.ui.base.ActivityExtension;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.view.MuunLockOverlay;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import icepick.State;
import rx.Single;

import javax.inject.Inject;

import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;

@PerActivity
public class ApplicationLockExtension extends ActivityExtension {

    private static final int AFTER_SUCCESS_DELAY_MS = 200;

    private static final int CUSTOM_SOFT_INPUT_DISABLED = (
            // NOTE: always keep ADJUST_RESIZE, or it will never work again, even if re-set later.
            // Only remove if willing to experiment for hours and then `reset --hard`.
            SOFT_INPUT_STATE_ALWAYS_HIDDEN | SOFT_INPUT_ADJUST_RESIZE
    );

    private final Navigator navigator;
    private final ApplicationLockManager lockManager;
    private final ExecutionTransformerFactory executionTransformerFactory;

    private final Analytics analytics;

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
                                    Analytics analytics) {
        this.navigator = navigator;
        this.lockManager = lockManager;
        this.executionTransformerFactory = executionTransformerFactory;
        this.analytics = analytics;
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
        if (lockOverlay == null) {
            lockOverlay = new MuunLockOverlay(getActivity());

            lockOverlay.setFingerprintAllowed(false); // never, for now
            lockOverlay.setListener(new BoundLockOverlayListener());

            lockOverlay.attachToRoot();
            disableSoftInput();

            analytics.report(new AnalyticsEvent.S_PIN_LOCKED());
        }

        updateLockOverlayAttempts();
    }

    private void hideLockOverlay() {
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
        lockOverlay.setRemainingAttempts(
                lockManager.getRemainingAttempts(),
                lockManager.getMaxAttempts()
        );
    }

    private void onUnlockAttemptFailure() {
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
        if (lockOverlay != null) { // avoid race conditions
            lockOverlay.reportSuccess();
            analytics.report(new AnalyticsEvent.E_PIN(AnalyticsEvent.PIN_TYPE.CORRECT));
            new Handler().postDelayed(this::hideLockOverlay, AFTER_SUCCESS_DELAY_MS);
        }
    }

    private class BoundLockOverlayListener implements MuunLockOverlay.LockOverlayListener {
        @Override
        public void onPinEntered(String pin) {
            Single.fromCallable(() -> lockManager.tryUnlockWithPin(pin))
                    .compose(executionTransformerFactory.getSingleAsyncExecutor())
                    .subscribe((isUnlocked) -> {
                        if (isUnlocked) {
                            onUnlockAttemptSuccess();
                        } else {
                            onUnlockAttemptFailure();
                        }
                    });
        }

        @Override
        public void onFingerprintEntered() {
            lockManager.tryUnlockWithFingerprint();
            afterSuccessOrFailure();
        }

        private void afterSuccessOrFailure() {
            if (lockManager.isLockSet()) {
                onUnlockAttemptFailure();
            } else {
                onUnlockAttemptSuccess();
            }
        }
    }
}
