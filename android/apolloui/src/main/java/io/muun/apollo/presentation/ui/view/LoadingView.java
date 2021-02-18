package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.annotation.StringRes;
import butterknife.BindView;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class LoadingView extends MuunView {

    static final ViewProps<LoadingView> viewProps = new ViewProps.Builder<LoadingView>()
            .addString(R.attr.title, LoadingView::setTitle)
            .build();

    @BindView(R.id.loading_view_title)
    TextView title;

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

    public void setTitle(@NotNull String text) {
        title.setText(text);
    }
}
