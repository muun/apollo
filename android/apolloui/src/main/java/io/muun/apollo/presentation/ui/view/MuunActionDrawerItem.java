package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.MuunActionDrawerItemBinding;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import kotlin.jvm.functions.Function3;

public class MuunActionDrawerItem extends MuunView<MuunActionDrawerItemBinding> {

    public MuunActionDrawerItem(Context context) {
        super(context);
    }

    public MuunActionDrawerItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunActionDrawerItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, MuunActionDrawerItemBinding>
            bindingInflater() {
        return MuunActionDrawerItemBinding::inflate;
    }

    private int getEnabledTintColor() {
        return ContextCompat.getColor(getContext(), R.color.text_secondary_color);
    }

    private int getDisabledTintColor() {
        return ContextCompat.getColor(getContext(), R.color.disabled_color);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setTint(enabled ? getEnabledTintColor() : getDisabledTintColor());
    }

    public void setTint(@ColorInt int tintColor) {
        UiUtils.setTintColor(getBinding().muunActionDrawerItemIcon, tintColor);
        getBinding().muunActionDrawerItemLabel.setTextColor(tintColor);
    }

    public void setIcon(@DrawableRes int iconRes) {
        getBinding().muunActionDrawerItemIcon.setImageResource(iconRes);
        getBinding().muunActionDrawerItemIcon.setVisibility(View.VISIBLE);
        setTint(getEnabledTintColor());
    }

    public void setIcon(Drawable drawable) {
        getBinding().muunActionDrawerItemIcon.setImageDrawable(drawable);
        getBinding().muunActionDrawerItemIcon.setVisibility(View.VISIBLE);
    }

    /**
     * Set this item's label.
     */
    public void setLabel(String text) {
        if (text != null) {
            getBinding().muunActionDrawerItemLabel.setText(text);
        }
    }
}
