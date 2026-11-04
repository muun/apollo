package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.LoadingViewBinding;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import kotlin.jvm.functions.Function3;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

public class LoadingView extends MuunView<LoadingViewBinding> {

    static final ViewProps<LoadingView> viewProps = new ViewProps.Builder<LoadingView>()
            .addStringJava(R.attr.title, LoadingView::setTitle)
            .addBoolean(R.attr.showTitle, LoadingView::showTitle)
            .build();

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, LoadingViewBinding> bindingInflater() {
        return LoadingViewBinding::inflate;
    }

    public LoadingView(Context context) {
        super(context);
    }

    public LoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LoadingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        viewProps.transfer(attrs, this);
    }

    public void setTitle(@StringRes int resId) {
        getBinding().loadingViewTitle.setText(resId);
    }

    public void setTitle(@NotNull CharSequence text) {
        getBinding().loadingViewTitle.setText(text);
        getBinding().loadingViewSpinner.setVisibility(View.VISIBLE);
    }

    public void setDescription(@NotNull CharSequence text) {
        getBinding().loadingViewDesc.setText(text);
        getBinding().loadingViewDesc.setVisibility(View.VISIBLE);
    }

    public void showTitle(Boolean showText) {
        getBinding().loadingViewTitle.setVisibility(showText ? View.VISIBLE : View.GONE);
    }

    public void tintProgressBar(@ColorInt int color) {
        DrawableCompat.setTint(getBinding().loadingViewSpinner.getIndeterminateDrawable(), color);
    }

    public void resetViewState() {
        getBinding().loadingViewDesc.setVisibility(View.GONE);
        tintProgressBar(ContextCompat.getColor(getContext(), R.color.blue));
    }
}
