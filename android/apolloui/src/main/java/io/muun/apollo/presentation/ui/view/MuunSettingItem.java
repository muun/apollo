package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.MuunSettingItemBinding;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import kotlin.jvm.functions.Function3;

import javax.annotation.Nullable;

public class MuunSettingItem extends MuunView<MuunSettingItemBinding> {

    static final ViewProps<MuunSettingItem> viewProps = new ViewProps.Builder<MuunSettingItem>()
            .addStringJava(R.attr.label, MuunSettingItem::setLabel)
            .addStringJava(R.attr.description, MuunSettingItem::setDescription)
            .addRefJava(R.attr.icon, MuunSettingItem::setIcon)
            .addColorList(R.attr.iconTint, MuunSettingItem::setIconColor)
            .build();

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, MuunSettingItemBinding> bindingInflater() {
        return MuunSettingItemBinding::inflate;
    }

    public MuunSettingItem(Context context) {
        super(context);
    }

    public MuunSettingItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunSettingItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        viewProps.transfer(attrs, this);
    }

    public void setLabel(CharSequence labelText) {
        getBinding().settingItemLabel.setText(labelText);
    }

    public void setDescription(@StringRes int descriptionRes) {
        showDescription(getContext().getString(descriptionRes));
        getBinding().settingItemDescriptionIcon.setVisibility(GONE);
    }

    public void setDescription(CharSequence descriptionText) {
        showDescription(descriptionText);
        getBinding().settingItemDescriptionIcon.setVisibility(GONE);
    }

    public void setDescription(CharSequence descriptionText, @DrawableRes int descriptionIconRes) {
        showDescription(descriptionText);
        setDescriptionIcon(descriptionIconRes);
    }

    private void showDescription(CharSequence descriptionText) {
        final TextView description = getBinding().settingItemDescription;
        description.setText(descriptionText);
        description.setVisibility(View.VISIBLE);
    }

    private void setDescriptionIcon(@DrawableRes int descriptionIconRes) {
        final ImageView descriptionIcon = getBinding().settingItemDescriptionIcon;
        descriptionIcon.setImageResource(descriptionIconRes);
        descriptionIcon.setVisibility(VISIBLE);
    }

    public void setIcon(@DrawableRes int resId) {
        setIcon(ContextCompat.getDrawable(getContext(), resId));
    }

    public void setIcon(Drawable drawable) {
        final ImageView icon = getBinding().settingItemIcon;
        icon.setImageDrawable(drawable);
        icon.setVisibility(drawable != null ? View.VISIBLE : View.GONE);
    }

    public void setIconColor(ColorStateList color) {
        ImageViewCompat.setImageTintList(getBinding().settingItemIcon, color);
    }
}
