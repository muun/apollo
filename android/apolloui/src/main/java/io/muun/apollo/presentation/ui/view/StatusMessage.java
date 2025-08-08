package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.StatusMessageBinding;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import javax.annotation.Nullable;

public class StatusMessage extends MuunView {

    static final ViewProps<StatusMessage> viewProps = new ViewProps.Builder<StatusMessage>()
            .addSizeJava(android.R.attr.textSize, StatusMessage::setTextSize)
            .build();

    private StatusMessageBinding binding;

    @Override
    public Function1<View, ViewBinding> viewBinder() {
        return StatusMessageBinding::bind;
    }

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

        binding = (StatusMessageBinding) getBinding();
        viewProps.transfer(attrs, this);
    }

    /**
     * Set this StatusMessage main image, from a Drawable resource.
     */
    public void setImage(@DrawableRes int resId) {
        binding.messageImage.setImageResource(resId);
    }

    /**
     * Set this StatusMessage main text, from a String resource.
     */
    public void setText(@StringRes int resId) {
        binding.messageText.setText(resId);
    }

    /**
     * Set this StatusMessage main text, from a CharSequence.
     */
    public void setText(CharSequence text) {
        binding.messageText.setText(text);
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
        binding.messageText.setTextSize(unit, size);
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

        final ImageView imageView = binding.messageImage;
        final Drawable warningIcon =
                ContextCompat.getDrawable(this.getContext(), R.drawable.ic_baseline_warning_24px);
        final int warningColor = ContextCompat.getColor(this.getContext(), R.color.warning_color);
        imageView.setImageDrawable(warningIcon);
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(warningColor));
        imageView.setVisibility(showIcon ? View.VISIBLE : View.GONE);

        binding.messageText.setText(TextUtils.concat(
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
        final ImageView imageView = binding.messageImage;
        final Drawable errorIcon =
                ContextCompat.getDrawable(this.getContext(), R.drawable.error_badge);
        final int errorColor = ContextCompat.getColor(this.getContext(), R.color.error_color);
        imageView.setImageDrawable(errorIcon);
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(errorColor));

        binding.messageText.setText(TextUtils.concat(
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
