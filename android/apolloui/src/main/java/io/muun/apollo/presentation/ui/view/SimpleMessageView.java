package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SimpleMessageViewBinding;
import io.muun.apollo.presentation.ui.utils.ExtensionsKt;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.StringRes;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import javax.annotation.Nullable;

public class SimpleMessageView extends MuunView {

    private SimpleMessageViewBinding binding;

    @Override
    public Function1<View, ViewBinding> viewBinder() {
        return SimpleMessageViewBinding::bind;
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
        binding = (SimpleMessageViewBinding) getBinding();
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
        final TextView title = binding.simpleMessageTitle;
        if (resId != 0) {
            title.setText(ExtensionsKt.getStyledString(this, resId));
            title.setVisibility(View.VISIBLE);

        } else {
            title.setVisibility(View.GONE);
        }
    }

    public void setSubtitle(@StringRes int resId) {
        binding.simpleMessageText.setText(ExtensionsKt.getStyledString(this, resId));
    }

    /**
     * Set the (optional) action text content.
     */
    public void setAction(@StringRes int resId) {
        final MuunButton action = binding.simpleMessageButton;
        if (resId != 0) {
            action.setText(resId);
            action.setVisibility(View.VISIBLE);

        } else {
            action.setVisibility(View.GONE);
        }
    }

    public void setOnActionClickListener(OnClickListener listener) {
        binding.simpleMessageButton.setOnClickListener(listener);
    }
}
