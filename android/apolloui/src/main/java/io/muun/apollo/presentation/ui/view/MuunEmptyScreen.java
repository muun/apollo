package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import butterknife.BindView;
import kotlin.Unit;
import rx.functions.Func1;

import javax.annotation.Nullable;

public class MuunEmptyScreen extends MuunView {

    static final ViewProps<MuunEmptyScreen> viewProps = new ViewProps.Builder<MuunEmptyScreen>()
            .addRef(R.attr.icon, MuunEmptyScreen::setIcon)
            .addString(R.attr.title, MuunEmptyScreen::setTitle)
            .addRef(R.attr.subtitle, MuunEmptyScreen::setSubtitle)
            .addRef(R.attr.action, MuunEmptyScreen::setAction)
            .build();

    @BindView(R.id.muun_empty_screen_icon)
    ImageView icon;

    @BindView(R.id.muun_empty_screen_title)
    TextView title;

    @BindView(R.id.muun_empty_screen_text)
    TextView text;

    @BindView(R.id.muun_empty_screen_button)
    MuunButton action;

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
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        viewProps.transfer(attrs, this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_empty_screen;
    }

    public void setIcon(@DrawableRes int resId) {
        icon.setImageResource(resId);
    }

    /**
     * Set the (optional) title content.
     */
    public void setTitle(CharSequence text) {
        if (text != null) {
            title.setText(text);
            title.setVisibility(View.VISIBLE);

        } else {
            title.setVisibility(View.GONE);
        }
    }

    public void setSubtitle(@StringRes int resId) {
        final StyledStringRes str = new StyledStringRes(getContext(), resId, this::onLinkClick);
        text.setText(str.toCharSequence());
    }

    /**
     * Set the (optional) action button text content.
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
