package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import butterknife.BindView;
import icepick.State;

import javax.annotation.Nullable;

public class MuunButton extends MuunView {

    static final ViewProps<MuunButton> viewProps = new ViewProps.Builder<MuunButton>()
            .addString(android.R.attr.text, MuunButton::setText)
            .addSize(android.R.attr.textSize, MuunButton::setTextSize)
            .addDimension(android.R.attr.layout_width, MuunButton::setWidthSpec)
            .addRef(android.R.attr.background, MuunButton::setBackgroundResource)
            .addBoolean(android.R.attr.textAllCaps, MuunButton::setTextAllCaps)
            .addSize(android.R.attr.paddingLeft, MuunButton::setPaddingLeft)
            .addSize(android.R.attr.paddingTop, MuunButton::setPaddingTop)
            .addSize(android.R.attr.paddingRight, MuunButton::setPaddingRight)
            .addSize(android.R.attr.paddingBottom, MuunButton::setPaddingBottom)
            .addInt(android.R.attr.textStyle, MuunButton::setTextStyle)
            .addColorList(android.R.attr.textColor, MuunButton::setTextColor)
            .addInt(android.R.attr.typeface, MuunButton::setTypeface)
            .addString(android.R.attr.fontFamily, MuunButton::setFontFamily)
            .build();

    public static final int TEXTSTYLE_UNDEFINED = -1;

    // Taken from TextView constants. See pending TODO regarding this
    private static final int SANS = 1;
    private static final int SERIF = 2;
    private static final int MONOSPACE = 3;

    @BindView(R.id.muun_button_button)
    Button button;

    @BindView(R.id.muun_button_progress_bar)
    ProgressBar progressBar;

    @BindView(R.id.muun_button_cover)
    TextView coverView;

    @State
    protected boolean isLoading;

    @State
    protected boolean isEnabled;

    @State
    protected Integer backgroundRes;

    @State
    protected String buttonText;

    @State
    protected String coverText;

    // These fields do not use @State because they are only set from attrs (and/or at creation)
    // if they could change after creation, they should use it.
    private int textStyle;
    private Typeface typeface;
    private String fontFamily;

    public MuunButton(Context context) {
        super(context);
    }

    public MuunButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_button;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        // Setting defaults in here because setUp is called INSIDE the view constructor
        textStyle = TEXTSTYLE_UNDEFINED;
        isEnabled = true;

        super.setUp(context, attrs);
        viewProps.transfer(attrs, this);

        setTypefaceFromAttrs();

        if (backgroundRes == null) {
            backgroundRes = R.drawable.muun_button_default_bg;
        }

        // Normally, progressBar color is set through `colorAccent`, but we don't use themes:
        progressBar.getIndeterminateDrawable()
                .setColorFilter(button.getCurrentTextColor(), PorterDuff.Mode.MULTIPLY);

        updateFromState();
    }

    @Override
    protected void onRestoreInstanceState(Parcelable parcelable) {
        super.onRestoreInstanceState(parcelable);
        setLoading(isLoading);
        setEnabled(isEnabled);
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener clickListener) {
        button.setOnClickListener(clickListener);
    }

    @Override
    public boolean callOnClick() {
        /*
         * This method goes hand in hand with @setOnClickListener. If we assign #onClickListener
         * to an inner view, we should redirect @callOnClick too. A similar case could be made for
         * #performOnClick.
         */
        return button.callOnClick();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        updateFromState();
    }

    @Override
    public void setBackgroundResource(@DrawableRes int resId) {
        backgroundRes = resId;
        updateFromState();
    }

    public void setText(@NonNull CharSequence text) {
        buttonText = text.toString();
        button.setText(text);
    }

    public void setText(@StringRes int resid) {
        setText(getContext().getString(resid));
    }

    /**
     * Set button's text size, in px.
     */
    public void setTextSize(int pixelSize) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, pixelSize);
    }

    /**
     * Set text size to a given unit and value.  See {@link
     * TypedValue} for the possible dimension units.
     */
    public void setTextSize(int unit, float size) {
        button.setTextSize(unit, size);
    }

    public void setTextAllCaps(boolean allCaps) {
        button.setAllCaps(allCaps);
    }

    public void setTextColor(int color) {
        button.setTextColor(color);
    }

    public void setTextColor(ColorStateList colors) {
        button.setTextColor(colors);
    }

    /**
     * Set button's left padding, in px.
     */
    public void setPaddingLeft(int paddingLeft) {
        super.setPadding(0, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        UiUtils.setPaddingLeft(button, paddingLeft);
    }

    /**
     * Set button's top padding, in px.
     */
    public void setPaddingTop(int paddingTop) {
        super.setPadding(getPaddingLeft(), 0, getPaddingRight(), getPaddingBottom());
        UiUtils.setPaddingTop(button, paddingTop);
    }

    /**
     * Set button's right padding, in px.
     */
    public void setPaddingRight(int paddingRight) {
        super.setPadding(getPaddingLeft(), getPaddingTop(), 0, getPaddingBottom());
        UiUtils.setPaddingRight(button, paddingRight);
    }

    /**
     * Set button's bottom padding, in px.
     */
    public void setPaddingBottom(int paddingBottom) {
        super.setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), 0);
        UiUtils.setPaddingBottom(button, paddingBottom);
    }

    public void setWidth(int widthSpec) {
        setWidthSpec(widthSpec);
        requestLayout();
    }

    /**
     * Set the loading state on this button.
     */
    public void setLoading(boolean isLoading) {
        this.isLoading = isLoading;
        updateFromState();
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setCoverText(@Nullable String coverText) {
        this.coverText = coverText;
        updateFromState();
    }

    /**
     * Set button's typeface and style in which the text should be displayed.
     *
     * @see TextView#setTypeface(android.graphics.Typeface, int)
     */
    public void setTypeface(Typeface tf, int style) {
        setTextStyle(style);
        this.typeface = tf;
        button.setTypeface(tf, style);
    }

    private void setTypeface(int typefaceIndex) {
        switch (typefaceIndex) {
            case SANS:
                this.typeface = Typeface.SANS_SERIF;
                break;

            case SERIF:
                this.typeface = Typeface.SERIF;
                break;

            case MONOSPACE:
                this.typeface = Typeface.MONOSPACE;
                break;

            default:
                break;
        }
    }

    private void setTextStyle(int style) {
        this.textStyle = style;
    }

    private void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    private void setWidthSpec(int widthSpec) {
        // widthSpec could be an exact size (in px) or MATCH_PARENT, WRAP_CONTENT constants
        button.getLayoutParams().width = widthSpec;
    }

    private void setTypefaceFromAttrs() {
        // @see android.widget.TextView#setTypefaceFromAttrs()
        if (fontFamily != null) {
            final Typeface tf = Typeface.create(fontFamily, textStyle);
            if (tf != null) {
                button.setTypeface(tf);
                return;
            }
        }

        if (textStyle != TEXTSTYLE_UNDEFINED || typeface != null) {
            button.setTypeface(typeface, textStyle);
        }
    }

    private void updateFromState() {
        if (coverText != null) {
            updateFromCoveredState();

        } else if (isLoading) {
            updateFromLoadingState();

        } else if (isEnabled) {
            updateFromEnabledState();

        } else {
            updateFromDisabledState();
        }
    }

    private void updateFromEnabledState() {
        button.setText(buttonText);
        button.setEnabled(true);
        button.setVisibility(View.VISIBLE);
        updateButtonBackground(backgroundRes, android.R.attr.state_enabled);

        progressBar.setVisibility(View.GONE);
        coverView.setVisibility(View.GONE);
    }

    private void updateFromDisabledState() {
        button.setText(buttonText);
        button.setEnabled(false);
        button.setVisibility(View.VISIBLE);
        updateButtonBackground(backgroundRes);

        progressBar.setVisibility(View.GONE);
        coverView.setVisibility(View.GONE);
    }

    private void updateFromLoadingState() {
        button.setText("");
        button.setEnabled(false);
        button.setVisibility(View.VISIBLE);
        updateButtonBackground(backgroundRes, android.R.attr.state_enabled);

        progressBar.setVisibility(View.VISIBLE);
        coverView.setVisibility(View.GONE);
    }

    private void updateFromCoveredState() {
        button.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        coverView.setText(coverText);
        coverView.setVisibility(View.VISIBLE);
    }

    private void updateButtonBackground(int newBackgroundRes, int ...backgroundStates) {
        button.setBackground(UiUtils.getDrawableForState(
                ContextCompat.getDrawable(getContext(), newBackgroundRes),
                backgroundStates
        ));
    }
}
