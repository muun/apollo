package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import butterknife.BindDrawable;
import rx.functions.Action0;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class MuunPinIndicator extends MuunView {

    public enum PinIndicatorState {
        NORMAL,
        ERROR,
        SUCCESS
    }

    public static final int FLASH_DURATION_MS = 1000;

    @BindDrawable(R.drawable.muun_bubble_on)
    Drawable bubbleBackgroundOn;

    @BindDrawable(R.drawable.muun_bubble_off)
    Drawable bubbleBackgroundOff;

    @BindDrawable(R.drawable.muun_bubble_success)
    Drawable bubbleBackgroundSuccess;

    @BindDrawable(R.drawable.muun_bubble_error)
    Drawable bubbleBackgroundError;

    private int progress;
    private PinIndicatorState state;

    private List<View> bubbles;

    public MuunPinIndicator(Context context) {
        super(context);
    }

    public MuunPinIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunPinIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_pin_indicator;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        final ViewGroup root = getRoot();

        // NOTE: could be adapted to hold a variable number of bubbles. Not necessary for now.
        final int numBubbles = root.getChildCount();
        bubbles = new ArrayList<>(numBubbles);

        for (int i = 0; i < numBubbles; i++) {
            bubbles.add(root.getChildAt(i));
        }

        state = PinIndicatorState.NORMAL;
        progress = 0;

        updateBubbles();
    }

    /**
     * Briefly animate the bubbles with an error background, invoking a callback when done.
     */
    public void flashState(PinIndicatorState state, @Nullable Action0 onComplete) {
        setState(state);
        resetStateAfterDelay(onComplete);
    }

    /**
     * Set the state of all bubbles.
     */
    public void setState(PinIndicatorState state) {
        this.state = state;
        updateBubbles();
    }

    /**
     * Set the number of bubbles turned on.
     */
    public void setProgress(int progress) {
        Preconditions.checkArgument(progress <= bubbles.size());

        this.progress = progress;
        updateBubbles();
    }

    private void resetStateAfterDelay(@Nullable Action0 onComplete) {
        postDelayed(
                () -> {
                    setState(PinIndicatorState.NORMAL);

                    if (onComplete != null) {
                        onComplete.call();
                    }
                },
                FLASH_DURATION_MS
        );
    }

    private void updateBubbles() {
        for (int i = 0; i < bubbles.size(); i++) {
            final View bubble = bubbles.get(i);

            if (state == PinIndicatorState.ERROR) {
                bubble.setBackground(bubbleBackgroundError);

            } else if (state == PinIndicatorState.SUCCESS) {
                bubble.setBackground(bubbleBackgroundSuccess);

            } else if (i < progress) {
                bubble.setBackground(bubbleBackgroundOn);

            } else {
                bubble.setBackground(bubbleBackgroundOff);
            }
        }
    }
}
