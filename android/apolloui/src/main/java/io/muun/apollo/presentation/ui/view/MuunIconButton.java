package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.MuunIconButtonBinding;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import kotlin.jvm.functions.Function3;

import javax.annotation.Nullable;

public class MuunIconButton extends MuunView<MuunIconButtonBinding> {

    static final ViewProps<MuunIconButton> viewProps = new ViewProps.Builder<MuunIconButton>()
            .addRefJava(R.attr.icon, MuunIconButton::setIcon)
            .addSizeJava(R.attr.iconSize, MuunIconButton::setIconSize)
            .addColorList(R.attr.color, MuunIconButton::setColor)
            .addStringJava(R.attr.label, MuunIconButton::setLabel)
            .addSizeJava(R.attr.labelSize, MuunIconButton::setLabelSize)
            .build();

    ColorStateList colorList;

    public MuunIconButton(Context context) {
        super(context);
    }

    public MuunIconButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunIconButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, MuunIconButtonBinding> bindingInflater() {
        return MuunIconButtonBinding::inflate;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        setClickable(true);
        setFocusable(true);
        setColor(ContextCompat.getColorStateList(getContext(), R.color.blue));

        viewProps.transfer(attrs, this);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateColor();
    }

    public void setIcon(@DrawableRes int resId) {
        getBinding().muunIconButtonImage.setImageResource(resId);
    }

    /**
     * Set the icon size.
     */
    public void setIconSize(int pixelSize) {
        getBinding().muunIconButtonImage.setLayoutParams(
                new LinearLayout.LayoutParams(pixelSize, pixelSize)
        );
        getBinding().muunIconButtonImage.requestLayout();
    }

    public void setColor(ColorStateList colorList) {
        this.colorList = colorList;
        updateColor();
    }

    public void setLabel(CharSequence content) {
        getBinding().muunIconButtonText.setText(content);
        setLabelVisible(getBinding().muunIconButtonText.length() > 0);
    }

    /**
     * Set button's text size, in px.
     */
    public void setLabelSize(int pixelSize) {
        setLabelSize(TypedValue.COMPLEX_UNIT_PX, pixelSize);
    }

    /**
     * Set text size to a given unit and value.  See {@link
     * TypedValue} for the possible dimension units.
     */
    public void setLabelSize(int unit, float size) {
        getBinding().muunIconButtonText.setTextSize(unit, size);
    }

    public void setLabelVisible(boolean isVisible) {
        getBinding().muunIconButtonText.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void updateColor() {
        if (colorList == null) {
            return;
        }
        final int stateColor = colorList.getColorForState(getDrawableState(), Color.TRANSPARENT);

        getBinding().muunIconButtonImage.setColorFilter(stateColor);
        getBinding().muunIconButtonText.setTextColor(stateColor);
    }
}
