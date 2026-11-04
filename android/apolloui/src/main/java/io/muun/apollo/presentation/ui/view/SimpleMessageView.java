package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SimpleMessageViewBinding;
import io.muun.apollo.presentation.ui.utils.ExtensionsKt;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.StringRes;
import kotlin.jvm.functions.Function3;

import javax.annotation.Nullable;

public class SimpleMessageView extends MuunView<SimpleMessageViewBinding> {

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, SimpleMessageViewBinding>
            bindingInflater() {
        return SimpleMessageViewBinding::inflate;
    }

    static final ViewProps<SimpleMessageView> viewProps = new ViewProps.Builder<SimpleMessageView>()
            .addRefJava(R.attr.title, SimpleMessageView::setTitle)
            .addRefJava(R.attr.subtitle, SimpleMessageView::setSubtitle)
            .addRefJava(R.attr.action, SimpleMessageView::setAction)
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

    /**
     * Set the (optional) title content.
     */
    public void setTitle(@StringRes int resId) {
        final TextView title = getBinding().simpleMessageTitle;
        if (resId != 0) {
            title.setText(ExtensionsKt.getStyledString(this, resId));
            title.setVisibility(View.VISIBLE);

        } else {
            title.setVisibility(View.GONE);
        }
    }

    public void setSubtitle(@StringRes int resId) {
        getBinding().simpleMessageText.setText(ExtensionsKt.getStyledString(this, resId));
    }

    /**
     * Set the (optional) action text content.
     */
    public void setAction(@StringRes int resId) {
        final MuunButton action = getBinding().simpleMessageButton;
        if (resId != 0) {
            action.setText(resId);
            action.setVisibility(View.VISIBLE);

        } else {
            action.setVisibility(View.GONE);
        }
    }

    public void setOnActionClickListener(OnClickListener listener) {
        getBinding().simpleMessageButton.setOnClickListener(listener);
    }
}
