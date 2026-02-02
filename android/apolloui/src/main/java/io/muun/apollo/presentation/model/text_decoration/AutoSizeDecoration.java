package io.muun.apollo.presentation.model.text_decoration;

import io.muun.common.utils.Preconditions;

import android.text.TextPaint;
import android.util.TypedValue;

/**
 * This Decoration does auto-resizing of the text taking the largest value that fits, from
 * MIN_SIZE_DP to maxSizeDp.
 */
public class AutoSizeDecoration implements DecorationTransformation {

    private final TextPaint paint = new TextPaint();
    private final int maxSizePx;
    private float maxWidthPx;
    private final float minSizePx;

    private DecorationHandler target;
    private String currentText;

    /**
     * Constructor.
     */
    public AutoSizeDecoration(float maxSizePx, float maxWidthPx, float minSizePx) {
        this.maxSizePx = (int) maxSizePx;
        this.maxWidthPx = maxWidthPx;
        this.minSizePx = minSizePx;
    }

    /**
     * Yes. This could be just another constructor parameter. I preferred this approach to play
     * "nice" with {@link TextDecorator}. Another constructor (with this param) could be added,
     * but it's just not needed for now.
     */
    @Override
    public void setTarget(DecorationHandler target) {
        this.target = target;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // do nothing
    }

    @Override
    public void afterTextChanged(StringBuilder editable) {
        Preconditions.checkNotNull(target);

        this.currentText = editable.toString();
        triggerResize(currentText);
    }

    private void triggerResize(String text) {
        final float biggestTextSizeFittingTheContainer = nextSize(text);
        target.setTextSize(TypedValue.COMPLEX_UNIT_PX, biggestTextSizeFittingTheContainer);
    }

    /**
     * Set maximum width in pixels, and triggers a resize, as maxWidthPx is a core param to
     * calculate the desired text size.
     */
    public void setMaxWidthPx(float maxWidthPx) {
        this.maxWidthPx = maxWidthPx;
        triggerResize(currentText);
    }

    private float nextSize(String text) {
        float textSizePx = maxSizePx;
        float width;

        do {
            if (textSizePx < minSizePx) {
                return minSizePx;
            }

            paint.setTextSize(textSizePx);
            width = paint.measureText(text);

            textSizePx--;
        } while (width > maxWidthPx);

        return textSizePx;
    }
}