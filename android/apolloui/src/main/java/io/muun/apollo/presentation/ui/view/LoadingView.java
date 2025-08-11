package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.LoadingViewBinding;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class LoadingView extends MuunView {

    static final ViewProps<LoadingView> viewProps = new ViewProps.Builder<LoadingView>()
            .addStringJava(R.attr.title, LoadingView::setTitle)
            .addBoolean(R.attr.showTitle, LoadingView::showTitle)
            .build();

    private LoadingViewBinding binding;

    @Override
    public Function1<View, ViewBinding> viewBinder() {
        return LoadingViewBinding::bind;
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
        binding = (LoadingViewBinding) getBinding();
        viewProps.transfer(attrs, this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.loading_view;
    }

    public void setTitle(@StringRes int resId) {
        binding.loadingViewTitle.setText(resId);
    }

    public void setTitle(@NotNull CharSequence text) {
        binding.loadingViewTitle.setText(text);
        binding.loadingViewSpinner.setVisibility(View.VISIBLE);
    }

    public void setDescription(@NotNull CharSequence text) {
        binding.loadingViewDesc.setText(text);
        binding.loadingViewDesc.setVisibility(View.VISIBLE);
    }

    public void showTitle(Boolean showText) {
        binding.loadingViewTitle.setVisibility(showText ? View.VISIBLE : View.GONE);
    }

    public void tintProgressBar(@ColorInt int color) {
        DrawableCompat.setTint(binding.loadingViewSpinner.getIndeterminateDrawable(), color);
    }

    public void resetViewState() {
        binding.loadingViewDesc.setVisibility(View.GONE);
        tintProgressBar(ContextCompat.getColor(getContext(), R.color.blue));
    }
}
