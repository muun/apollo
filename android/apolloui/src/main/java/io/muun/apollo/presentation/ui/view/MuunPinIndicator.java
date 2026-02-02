package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.MuunPinIndicatorBinding;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;
import rx.functions.Action0;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class MuunPinIndicator extends MuunView {

    static final ViewProps<MuunPinIndicator> viewProps = new ViewProps.Builder<MuunPinIndicator>()
            .addInt(R.attr.length, MuunPinIndicator::setLength)
            .build();

    public enum PinIndicatorState {
        NORMAL,
        ERROR,
        SUCCESS
    }

    public static final int FLASH_DURATION_MS = 1000;

    private final Drawable bubbleBackgroundOn =
            ContextCompat.getDrawable(getContext(), R.drawable.muun_bubble_on);

    private final Drawable bubbleBackgroundOff =
            ContextCompat.getDrawable(getContext(), R.drawable.muun_bubble_off);

    private final Drawable bubbleBackgroundSuccess =
            ContextCompat.getDrawable(getContext(), R.drawable.muun_bubble_success);

    private final Drawable bubbleBackgroundError =
            ContextCompat.getDrawable(getContext(), R.drawable.muun_bubble_error);

    private MuunPinIndicatorBinding binding;

    @Override
    public Function1<View, ViewBinding> viewBinder() {
        return MuunPinIndicatorBinding::bind;
    }

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
    protected void setUp(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        binding = (MuunPinIndicatorBinding) getBinding();
        viewProps.transfer(attrs, this);
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

    /**
     * Set the number of bubbles to draw.
     */
    public void setLength(int length) {
        final ViewGroup root = binding.root;

        if (bubbles != null) {
            bubbles.clear();
        }
        bubbles = createBubbleViews(length);

        root.removeAllViews();
        for (int i = 0; i < bubbles.size(); i++) {
            root.addView(bubbles.get(i));
        }
        updateBubbles();
    }

    /**
     * Returns the number of bubbles currently displayed.
     */
    public int getLength() {
        return bubbles.size();
    }

    private List<View> createBubbleViews(int size) {
        final int bubbleSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12.f,
                getContext().getResources().getDisplayMetrics()
        );

        final ArrayList<View> bubbleViews = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            bubbleViews.add(createBubbleView(bubbleSize));
        }
        return bubbleViews;
    }

    private View createBubbleView(int size) {
        final View bubble = new View(getContext());
        final LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(size, size);
        layoutParams.setMarginEnd(size);
        bubble.setLayoutParams(layoutParams);

        return bubble;
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
