package io.muun.apollo.presentation.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.LinearLayout;

/**
 * An animated linear layout that can be collapsed and expanded.
 *
 * <p>Note: To create it already collapsed, set its visibility to gone.
 */
public class CollapsibleLinearLayout extends LinearLayout {


    public static final int DURATION_MILLIS = 200;

    private Animation.AnimationListener collapseAnimationListener;
    private Animation.AnimationListener expandAnimationListener;

    public CollapsibleLinearLayout(Context context) {
        super(context);
    }

    public CollapsibleLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CollapsibleLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setExpandAnimationListener(Animation.AnimationListener expandAnimationListener) {
        this.expandAnimationListener = expandAnimationListener;
    }

    public void setCollapseAnimationListener(
            Animation.AnimationListener collapseAnimationListener) {
        this.collapseAnimationListener = collapseAnimationListener;
    }

    /**
     * Expands the view to wrap_content height.
     *
     * <p>If already expanded, this method does nothing.
     */
    public void expand() {

        if (!isCollapsed()) {
            return;
        }

        measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        final int targetHeight = getMeasuredHeight();

        //APILEVEL(21): Animations are canceled if height is 0, so we start from 1.
        getLayoutParams().height = 1;
        setVisibility(View.VISIBLE);

        final Animation animation = new Animation() {

            @Override
            protected void applyTransformation(float interpolatedTime,
                                               Transformation transformation) {

                getLayoutParams().height = interpolatedTime == 1
                        ? LayoutParams.WRAP_CONTENT
                        : (int) (targetHeight * interpolatedTime);

                requestLayout();

            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }

        };

        animation.setDuration(DURATION_MILLIS);
        animation.setAnimationListener(expandAnimationListener);

        startAnimation(animation);
    }

    /**
     * Collapses the view decreasing its height and finally setting its visibility to gone.
     *
     * <p>If already collapsed, this method does nothing.
     */
    public void collapse() {

        if (isCollapsed()) {
            return;
        }

        final int initialHeight = getMeasuredHeight();

        final Animation animation = new Animation() {

            @Override
            protected void applyTransformation(float interpolatedTime,
                                               Transformation transformation) {

                if (interpolatedTime == 1) {
                    setVisibility(View.GONE);
                } else {
                    getLayoutParams().height = initialHeight
                            - (int) (initialHeight * interpolatedTime);
                    requestLayout();
                }

            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        animation.setDuration(DURATION_MILLIS);
        animation.setAnimationListener(collapseAnimationListener);

        startAnimation(animation);
    }

    public boolean isCollapsed() {
        return getVisibility() == GONE;
    }

}
