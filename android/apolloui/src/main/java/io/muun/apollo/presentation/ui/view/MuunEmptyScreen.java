package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.MuunEmptyScreenBinding;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;
import rx.functions.Func1;

import javax.annotation.Nullable;

public class MuunEmptyScreen extends MuunView<MuunEmptyScreenBinding> {

    static final ViewProps<MuunEmptyScreen> viewProps = new ViewProps.Builder<MuunEmptyScreen>()
            .addRefJava(R.attr.icon, MuunEmptyScreen::setIcon)
            .addStringJava(R.attr.title, MuunEmptyScreen::setTitle)
            .addRefJava(R.attr.subtitle, MuunEmptyScreen::setSubtitle)
            .addRefJava(R.attr.action, MuunEmptyScreen::setAction)
            .build();

    private Func1<String, Unit> onLinkClickListener;

    public MuunEmptyScreen(Context context) {
        super(context);
    }

    public MuunEmptyScreen(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunEmptyScreen(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, MuunEmptyScreenBinding> bindingInflater() {
        return MuunEmptyScreenBinding::inflate;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        viewProps.transfer(attrs, this);
    }

    public void setIcon(@DrawableRes int resId) {
        getBinding().muunEmptyScreenIcon.setImageResource(resId);
    }

    /**
     * Set the (optional) title content.
     */
    public void setTitle(CharSequence text) {
        if (text != null) {
            getBinding().muunEmptyScreenTitle.setText(text);
            getBinding().muunEmptyScreenTitle.setVisibility(View.VISIBLE);

        } else {
            getBinding().muunEmptyScreenTitle.setVisibility(View.GONE);
        }
    }

    public void setSubtitle(@StringRes int resId) {
        final StyledStringRes str = new StyledStringRes(getContext(), resId, this::onLinkClick);
        getBinding().muunEmptyScreenText.setText(str.toCharSequence());
    }

    /**
     * Set the (optional) action button text content.
     */
    public void setAction(@StringRes int resId) {
        if (resId != 0) {
            getBinding().muunEmptyScreenButton.setText(resId);
            getBinding().muunEmptyScreenButton.setVisibility(View.VISIBLE);
        } else {
            getBinding().muunEmptyScreenButton.setVisibility(View.GONE);
        }
    }

    public void setOnActionClickListener(OnClickListener listener) {
        getBinding().muunEmptyScreenButton.setOnClickListener(listener);
    }

    private Unit onLinkClick(String linkId) {
        if (onLinkClickListener != null) {
            onLinkClickListener.call(linkId);
        }

        return Unit.INSTANCE;
    }

    public void setOnLinkClickListener(Func1<String, Unit> listener) {
        this.onLinkClickListener = listener;
    }
}
