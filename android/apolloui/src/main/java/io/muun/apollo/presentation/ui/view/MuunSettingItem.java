package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import butterknife.BindView;

import javax.annotation.Nullable;

public class MuunSettingItem extends MuunView {

    static final ViewProps<MuunSettingItem> viewProps = new ViewProps.Builder<MuunSettingItem>()
            .addString(R.attr.label, MuunSettingItem::setLabel)
            .addString(R.attr.description, MuunSettingItem::setDescription)
            .addRef(R.attr.icon, MuunSettingItem::setIcon)
            .build();

    @BindView(R.id.setting_item_label)
    TextView label;

    @BindView(R.id.setting_item_description)
    TextView description;

    @BindView(R.id.setting_item_description_icon)
    ImageView descriptionIcon;

    @BindView(R.id.setting_item_icon)
    ImageView icon;

    public MuunSettingItem(Context context) {
        super(context);
    }

    public MuunSettingItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunSettingItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override protected int getLayoutResource() {
        return R.layout.muun_setting_item;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        viewProps.transfer(attrs, this);
    }

    public void setLabel(CharSequence labelText) {
        label.setText(labelText);
    }

    public void setDescription(CharSequence descriptionText) {
        showDescription(descriptionText);
        descriptionIcon.setVisibility(GONE);
    }

    public void setDescription(CharSequence descriptionText, @DrawableRes int descriptionIconRes) {
        showDescription(descriptionText);
        setDescriptionIcon(descriptionIconRes);
    }

    private void showDescription(CharSequence descriptionText) {
        description.setText(descriptionText);
        description.setVisibility(View.VISIBLE);
    }

    private void setDescriptionIcon(@DrawableRes int descriptionIconRes) {
        descriptionIcon.setImageResource(descriptionIconRes);
        descriptionIcon.setVisibility(VISIBLE);
    }

    public void setIcon(@DrawableRes int resId) {
        setIcon(ContextCompat.getDrawable(getContext(), resId));
    }

    public void setIcon(Drawable drawable) {
        icon.setImageDrawable(drawable);
        icon.setVisibility(drawable != null ? View.VISIBLE : View.GONE);
    }
}
