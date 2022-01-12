package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import butterknife.BindView;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class LoadingView extends MuunView {

    static final ViewProps<LoadingView> viewProps = new ViewProps.Builder<LoadingView>()
            .addStringJava(R.attr.title, LoadingView::setTitle)
            .addBoolean(R.attr.showTitle, LoadingView::showTitle)
            .build();

    @BindView(R.id.loading_view_spinner)
    ProgressBar progressBar;

    @BindView(R.id.loading_view_title)
    TextView title;

    @BindView(R.id.loading_view_desc)
    TextView description;

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

    @Override
    protected int getLayoutResource() {
        return R.layout.loading_view;
    }

    public void setTitle(@StringRes int resId) {
        title.setText(resId);
    }

    public void setTitle(@NotNull CharSequence text) {
        title.setText(text);
        progressBar.setVisibility(View.VISIBLE);
    }

    public void setDescription(@NotNull CharSequence text) {
        description.setText(text);
        description.setVisibility(View.VISIBLE);
    }

    public void showTitle(Boolean showText) {
        title.setVisibility(showText ? View.VISIBLE : View.GONE);
    }

    public void tintProgressBar(@ColorInt int color) {
        DrawableCompat.setTint(progressBar.getIndeterminateDrawable(), color);
    }

    public void resetViewState() {
        description.setVisibility(View.GONE);
        tintProgressBar(ContextCompat.getColor(getContext(), R.color.blue));
    }
}
