package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;

import javax.annotation.Nullable;

public class MuunActionItem extends MuunView {

    static final ViewProps<MuunActionItem> viewProps = new ViewProps.Builder<MuunActionItem>()
            .addString(R.attr.title, MuunActionItem::setTitle)
            .addString(R.attr.subtitle, MuunActionItem::setSubtitle)
            .addRef(R.attr.picture, MuunActionItem::setPicture)
            .build();

    @BindView(R.id.muun_action_item_picture)
    ImageView picture;

    @BindView(R.id.muun_action_item_title)
    TextView title;

    @BindView(R.id.muun_action_item_subtitle)
    TextView subtitile;

    @BindView(R.id.muun_action_item_divider)
    View divider;

    @BindView(R.id.muun_action_item_action)
    MuunIconButton muunIconButton;

    public MuunActionItem(Context context) {
        super(context);
    }

    public MuunActionItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunActionItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_action_item;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        viewProps.transfer(attrs, this);
    }

    public void setPicture(int resId) {
        picture.setImageResource(resId);
    }

    public void setTitle(int resId) {
        title.setText(resId);
    }

    public void setTitle(CharSequence text) {
        title.setText(text);
    }

    public void setSubtitle(CharSequence text) {
        subtitile.setText(text);
    }

    public void setSubtitle(int resId) {
        subtitile.setText(resId);
    }

    public void setActionVisible(boolean visible) {
        final int visibilityInt = visible ? VISIBLE : GONE;
        muunIconButton.setVisibility(visibilityInt);
    }

    public void setActionClickListener(OnClickListener clickListener) {
        muunIconButton.setOnClickListener(clickListener);
    }

    public void showDivider(boolean visible) {
        divider.setVisibility(visible ? VISIBLE : GONE);
    }

    @Override
    public void setOnClickListener(OnClickListener clickListener) {
        getChildAt(0).setOnClickListener(clickListener);
    }
}
