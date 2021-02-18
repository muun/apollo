package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.ExtensionsKt;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.StringRes;
import butterknife.BindView;

import javax.annotation.Nullable;

public class SimpleMessageView extends MuunView {

    @BindView(R.id.simple_message_title)
    TextView title;

    @BindView(R.id.simple_message_text)
    TextView text;

    @BindView(R.id.simple_message_button)
    MuunButton action;

    static final ViewProps<SimpleMessageView> viewProps = new ViewProps.Builder<SimpleMessageView>()
            .addRef(R.attr.title, SimpleMessageView::setTitle)
            .addRef(R.attr.subtitle, SimpleMessageView::setSubtitle)
            .addRef(R.attr.action, SimpleMessageView::setAction)
            .build();

    public SimpleMessageView(Context context) {
        super(context);
    }

    public SimpleMessageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SimpleMessageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        viewProps.transfer(attrs, this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.simple_message_view;
    }

    /**
     * Set the (optional) title content.
     */
    public void setTitle(@StringRes int resId) {
        if (resId != 0) {
            title.setText(ExtensionsKt.getStyledString(this, resId));
            title.setVisibility(View.VISIBLE);

        } else {
            title.setVisibility(View.GONE);
        }
    }

    public void setSubtitle(@StringRes int resId) {
        text.setText(ExtensionsKt.getStyledString(this, resId));
    }

    /**
     * Set the (optional) action text content.
     */
    public void setAction(@StringRes int resId) {
        if (resId != 0) {
            action.setText(resId);
            action.setVisibility(View.VISIBLE);

        } else {
            action.setVisibility(View.GONE);
        }
    }

    public void setOnActionClickListener(OnClickListener listener) {
        action.setOnClickListener(listener);
    }
}
