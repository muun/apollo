package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import butterknife.BindView;
import rx.functions.Action0;
import timber.log.Timber;

import javax.annotation.Nullable;


public class MuunLockOverlay extends MuunView {

    private static final int CONCURRENT_ACCESS_WINDOW_MS = 500;

    public interface LockOverlayListener {
        /**
         * This method will be called once a pin is fully/completely entered.
         */
        void onPinEntered(String pin);
    }

    @BindView(R.id.unlock_pin_input)
    MuunPinInput pinInput;

    private LockOverlayListener listener;

    private String lastPin;

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
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#attachToRoot");
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
        new Handler().postDelayed(this::resetLastPin, CONCURRENT_ACCESS_WINDOW_MS);
    }

    /**
     * Report to the Overlay that an unlock attempt succeeded.
     */
    public void reportSuccess() {
        Timber.i("MuunLockOverlay: reportSuccess");

        pinInput.setSuccess();
        new Handler().postDelayed(this::resetLastPin, CONCURRENT_ACCESS_WINDOW_MS);
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

    private void resetLastPin() {
        lastPin = null;
    }
}
