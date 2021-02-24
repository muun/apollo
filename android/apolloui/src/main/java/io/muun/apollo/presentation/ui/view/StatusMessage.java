package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.widget.ImageViewCompat;
import butterknife.BindColor;
import butterknife.BindDrawable;
import butterknife.BindView;

import javax.annotation.Nullable;

public class StatusMessage extends MuunView {

    static final ViewProps<StatusMessage> viewProps = new ViewProps.Builder<StatusMessage>()
            .addSize(android.R.attr.textSize, StatusMessage::setTextSize)
            .build();

    @BindView(R.id.message_image)
    ImageView imageView;

    @BindView(R.id.message_text)
    TextView textView;

    @BindDrawable(R.drawable.alert_badge)
    Drawable warningIcon;

    @BindColor(R.color.warning_color)
    int warningColor;

    @BindDrawable(R.drawable.error_badge)
    Drawable errorIcon;

    @BindColor(R.color.error_color)
    int errorColor;

    public StatusMessage(Context context) {
        super(context);
    }

    public StatusMessage(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StatusMessage(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.status_message;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        viewProps.transfer(attrs, this);
    }

    public void setImage(@DrawableRes int resId) {
        imageView.setImageResource(resId);
    }

    public void setText(@StringRes int resId) {
        textView.setText(resId);
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    /**
     * Set text size, in px.
     */
    public void setTextSize(int pixelSize) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, pixelSize);
    }

    /**
     * Set text size to a given unit and value.  See {@link
     * TypedValue} for the possible dimension units.
     */
    public void setTextSize(int unit, float size) {
        textView.setTextSize(unit, size);
    }

    /**
     * Predefined way to show a warning message.
     */
    public void setWarning(@StringRes int mainMessage, @StringRes int desc) {
        setWarning(mainMessage, desc, true, '.');
    }

    /**
     * Predefined way to show a warning message.
     */
    public void setWarning(@StringRes int mainMessage,
                           @StringRes int desc,
                           boolean showIcon,
                           char separator) {

        setWarning(
                getContext().getString(mainMessage),
                getContext().getString(desc),
                showIcon,
                separator
        );

    }

    /**
     * Predefined way to show a warning message.
     */
    public void setWarning(String mainMessage,
                           CharSequence desc,
                           boolean showIcon,
                           char separator) {

        imageView.setImageDrawable(warningIcon);
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(warningColor));
        imageView.setVisibility(showIcon ? View.VISIBLE : View.GONE);

        textView.setText(TextUtils.concat(
                highlight(mainMessage + separator, warningColor),
                " ",
                desc
        ));

        setVisibility(View.VISIBLE);
    }

    /**
     * Predefined way to show an error message.
     */
    public void setError(@StringRes int mainMessage, @StringRes int description) {
        setError(getContext().getString(mainMessage), getContext().getString(description));
    }

    /**
     * Predefined way to show an error message.
     */
    public void setError(String mainMessage, CharSequence description) {
        imageView.setImageDrawable(errorIcon);
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(errorColor));

        textView.setText(TextUtils.concat(
                highlight(mainMessage + ".", errorColor),
                " ",
                description
        ));

        setVisibility(View.VISIBLE);
    }

    private RichText highlight(String text, int color) {
        return new RichText(text)
                .setForegroundColor(color)
                .setFontFamily("sans-serif-medium");
    }
}
