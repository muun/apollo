package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.OS;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import butterknife.BindView;
import icepick.State;
import rx.functions.Action0;

import javax.annotation.Nullable;


public class MuunLockOverlay extends MuunView {

    public interface LockOverlayListener {
        /**
         * This method will be called once a pin is successfully entered.
         */
        void onPinEntered(String pin);

        /**
         * This method will be called once a fingerprint is successfully entered.
         */
        void onFingerprintEntered();
    }

    @BindView(R.id.unlock_pin_input)
    MuunPinInput pinInput;

    @State
    boolean isFingerprintAllowed;

    private boolean isFingerprintEnabled; // could be allowed but not available or paused
    private CancellationSignal cancelFingerprint;

    private LockOverlayListener listener;

    public MuunLockOverlay(Context context) {
        super(context);
    }

    public MuunLockOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunLockOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_lock_overlay;
    }

    @Override
    protected void setUp(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        pinInput.setListener(this::onPinEntered);
    }

    /**
     * Attach this Overlay to the root view of its Context, covering the parent Activity.
     */
    public void attachToRoot() {
        final ViewGroup contentView = getActivityContentView();
        contentView.getChildAt(0).setVisibility(View.INVISIBLE);

        ViewCompat.setElevation(this, Float.MAX_VALUE);

        contentView.addView(this, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    /**
     * Detach this Overlay from the root view of its Context, uncovering the parent Activity.
     */
    public void detachFromRoot() {
        final ViewGroup contentView = getActivityContentView();

        if (contentView.equals(getParent())) {
            contentView.removeView(this);
            contentView.getChildAt(0).setVisibility(View.VISIBLE);
            contentView.requestLayout();
            contentView.invalidate();
        }
    }

    /**
     * Allow the Overlay to read fingerprints, if able.
     */
    public void setFingerprintAllowed(boolean isAllowed) {
        this.isFingerprintAllowed = isAllowed;

        if (isFingerprintAllowed && !isFingerprintEnabled && getVisibility() == View.VISIBLE) {
            tryEnableFingerprint();
        }
    }

    /**
     * Set the remaining and total attempts.
     */
    public void setRemainingAttempts(int remainingAttempts, int maxAttempts) {
        Preconditions.checkArgument(remainingAttempts <= maxAttempts);

        pinInput.setRemainingAttempts(remainingAttempts);
        pinInput.setRemainingAttemptsVisible(remainingAttempts < maxAttempts);
    }

    /**
     * Report to the Overlay that an unlock attempt failed.
     */
    public void reportError(Action0 onComplete) {
        pinInput.flashError(onComplete);
    }

    /**
     * Report to the Overlay that an unlock attempt succeeded.
     */
    public void reportSuccess() {
        pinInput.setSuccess();
    }

    public void setListener(LockOverlayListener lockOverlayListener) {
        this.listener = lockOverlayListener;
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility != View.VISIBLE) {
            disableFingerprint();

        } else if (isFingerprintAllowed && !isFingerprintEnabled) {
            tryEnableFingerprint();
        }
    }

    private ViewGroup getActivityContentView() {
        final FragmentActivity activity = (FragmentActivity) getContext();

        return activity.findViewById(android.R.id.content);
    }

    private void onPinEntered(String pin) {
        if (this.listener != null) {
            this.listener.onPinEntered(pin);
        }
    }

    private void tryEnableFingerprint() {
        if (OS.supportsFingerprintAPI()) {
            enableFingerprintOnApi23();
        }
    }

    private void disableFingerprint() {
        if (cancelFingerprint != null) {
            cancelFingerprint.cancel();
            cancelFingerprint = null;
        }

        isFingerprintEnabled = false;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void enableFingerprintOnApi23() {
        final FingerprintManager fingerprintManager =
                (FingerprintManager) getContext().getSystemService(Context.FINGERPRINT_SERVICE);

        final boolean canEnableFingerprint = (fingerprintManager != null)
                && fingerprintManager.isHardwareDetected()
                && fingerprintManager.hasEnrolledFingerprints();

        if (! canEnableFingerprint) {
            return;
        }

        final FingerprintCallback listener = new FingerprintCallback();
        cancelFingerprint = new CancellationSignal();

        fingerprintManager.authenticate(
                null,              // No CryptoObject, we won't sign or encrypt with fingerprints
                cancelFingerprint, // A CancellationSignal to stop listening
                0,                 // No flags (fun fact: none actually existed when I wrote this)
                listener,          // Our custom bag of friendly callbacks
                null               // No special Handler, use the default
        );

        isFingerprintEnabled = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private class FingerprintCallback extends FingerprintManager.AuthenticationCallback {

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
            // TODO examine error code, decide what to do
        }

        @Override
        public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
            if (listener != null) {
                listener.onFingerprintEntered();
            }
        }

        @Override
        public void onAuthenticationFailed() {
            // TODO visual feedback of failed fingerprint auth
        }
    }
}
