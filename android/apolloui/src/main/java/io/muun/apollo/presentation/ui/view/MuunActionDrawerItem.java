package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import butterknife.BindColor;
import butterknife.BindView;

public class MuunActionDrawerItem extends MuunView {

    @BindView(R.id.muun_action_drawer_item_icon)
    ImageView icon;

    @BindView(R.id.muun_action_drawer_item_label)
    TextView label;

    @BindColor(R.color.text_secondary_color)
    int enabledTintColor;

    @BindColor(R.color.disabled_color)
    int disabledTintColor;

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
    protected int getLayoutResource() {
        return R.layout.muun_action_drawer_item;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setTint(enabled ? enabledTintColor : disabledTintColor);
    }

    public void setTint(@ColorInt int tintColor) {
        UiUtils.setTintColor(icon, tintColor);
        label.setTextColor(tintColor);
    }

    public void setIcon(@DrawableRes int iconRes) {
        icon.setImageResource(iconRes);
        icon.setVisibility(View.VISIBLE);
        setTint(enabledTintColor);
    }

    public void setIcon(Drawable drawable) {
        icon.setImageDrawable(drawable);
        icon.setVisibility(View.VISIBLE);
    }

    /**
     * Set this item's label.
     */
    public void setLabel(String text) {
        if (text != null) {
            label.setText(text);
        }
    }
}
