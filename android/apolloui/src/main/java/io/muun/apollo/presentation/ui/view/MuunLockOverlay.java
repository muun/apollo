package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.databinding.MuunLockOverlayBinding;
import io.muun.apollo.presentation.biometrics.BiometricsController;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import kotlin.jvm.functions.Function3;
import rx.functions.Action0;
import timber.log.Timber;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class MuunLockOverlay extends MuunView<MuunLockOverlayBinding> {

    private static final int CONCURRENT_ACCESS_WINDOW_MS = 500;

    public interface LockOverlayListener {
        /**
         * This method will be called once a pin is fully/completely entered.
         */
        void onPinEntered(String pin);

        void onUnlockUsingBiometrics();
    }

    @Inject
    BiometricsController biometricsController;

    private LockOverlayListener listener;

    private String lastPin;

    private MuunPinInput getPinInput() {
        return getBinding().unlockPinInput;
    }

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
    public Function3<LayoutInflater, ViewGroup, Boolean, MuunLockOverlayBinding> bindingInflater() {
        return MuunLockOverlayBinding::inflate;
    }

    @Override
    protected void setUp(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        if (getComponent() != null) {
            getComponent().inject(this);
        }

        getPinInput().setListener(this::onPinEntered);
        getPinInput().setBiometricsRequestedListener(this::onUnlockUsingBiometrics);
    }

    /**
     * Attach this Overlay to the root view of its Context, covering the parent Activity.
     */
    public void attachToRoot() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#attachToRoot");
        final ViewGroup contentView = getActivityContentView();
        contentView.getChildAt(0).setVisibility(View.INVISIBLE);

        ViewCompat.setElevation(this, Float.MAX_VALUE);

        contentView.addView(this, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (biometricsController.hasUserOptedInBiometrics()) {
            final var windowReadyListener = new ViewTreeObserver.OnWindowFocusChangeListener() {
                @Override
                public void onWindowFocusChanged(boolean hasFocus) {
                    if (hasFocus) {
                        getActivity().getWindow()
                                .getDecorView()
                                .getViewTreeObserver()
                                .removeOnWindowFocusChangeListener(this);

                        onUnlockUsingBiometrics();
                    }
                }
            };

            getActivity().getWindow()
                    .getDecorView()
                    .getViewTreeObserver()
                    .addOnWindowFocusChangeListener(windowReadyListener);
        }
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
     * Set the remaining and total attempts.
     */
    public void setRemainingAttempts(int remainingAttempts, int maxAttempts) {
        Preconditions.checkArgument(remainingAttempts <= maxAttempts);

        getPinInput().setRemainingAttempts(remainingAttempts);
        getPinInput().setRemainingAttemptsVisible(remainingAttempts < maxAttempts);
    }

    /**
     * Report to the Overlay that an unlock attempt failed.
     */
    public void reportError(Action0 onComplete) {
        getPinInput().flashError(onComplete);
        new Handler().postDelayed(this::resetLastPin, CONCURRENT_ACCESS_WINDOW_MS);
    }

    /**
     * Report to the Overlay that an unlock attempt succeeded.
     */
    public void reportSuccess() {
        Timber.i("MuunLockOverlay: reportSuccess");

        getPinInput().setSuccess();
        new Handler().postDelayed(this::resetLastPin, CONCURRENT_ACCESS_WINDOW_MS);
    }

    public void setPinLength(int length) {
        getPinInput().setPinLength(length);
    }

    public void setListener(LockOverlayListener lockOverlayListener) {
        this.listener = lockOverlayListener;
    }

    private ViewGroup getActivityContentView() {
        final FragmentActivity activity = (FragmentActivity) getContext();

        return activity.findViewById(android.R.id.content);
    }

    private void onPinEntered(String pin) {
        if (this.listener != null) {

            // We've seen in some devices "duplicated" events being fired almost instantaneously,
            // and we've observed that this causes trouble for keeping incorrectAttempts record in
            // secure storage. We'll try to avoid these duplicated events firing in a short
            // range from messing with secure storage's Keystore.
            if (pin.equals(lastPin)) {
                return; // If this is a "double fire"/"concurrent access" we'll dismiss it
            }

            lastPin = pin;

            this.listener.onPinEntered(pin);
        }
    }

    private void onUnlockUsingBiometrics() {
        if (this.listener != null) {
            this.listener.onUnlockUsingBiometrics();
        }
    }

    private void resetLastPin() {
        lastPin = null;
    }
}
