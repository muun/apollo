package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.databinding.MuunPillBinding;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import kotlin.jvm.functions.Function3;

import javax.annotation.Nullable;

public class MuunPill extends MuunView<MuunPillBinding> {

    static final ViewProps<MuunPill> viewProps = new ViewProps.Builder<MuunPill>()
            .addStringJava(android.R.attr.text, MuunPill::setText)
            .build();

    public MuunPill(Context context) {
        super(context);
    }

    public MuunPill(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunPill(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, MuunPillBinding> bindingInflater() {
        return MuunPillBinding::inflate;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        if (attrs != null) {
            viewProps.transfer(attrs, this);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        post(this::adjustPictureSize);
    }

    public void setText(CharSequence content) {
        getBinding().muunPillText.setText(content);
    }

    public void setPictureUri(@Nullable String pictureUri) {
        getBinding().muunPillPicture.setPictureUri(pictureUri);
    }

    public void setPictureUri(@Nullable Uri pictureUri) {
        getBinding().muunPillPicture.setPictureUri(pictureUri);
    }

    public void setPictureVisible(boolean isVisible) {
        getBinding().muunPillPicture.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void adjustPictureSize() {
        final int size = getMeasuredHeight();

        getBinding().muunPillPicture.getLayoutParams().width = size;
        getBinding().muunPillPicture.getLayoutParams().height = size;
    }
}
