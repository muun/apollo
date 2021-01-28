package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.exception.MissingCaseError;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import butterknife.BindView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import icepick.State;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MuunTextInput extends MuunView {

    public enum HelperTextVisibility {
        VISIBLE,
        VISIBLE_WHEN_FOCUSED,
        GONE
    }

    private static final int FIXED_IME_OPTIONS = (
            EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN
    );

    private static final int ANIMATION_DURATION = 200;

    private static final Interpolator
            LINEAR_OUT_SLOW_IN_INTERPOLATOR = new LinearOutSlowInInterpolator();

    public interface OnChangeListener {
        void onTextChanged(String newText);
    }

    public interface OnKeyboardNextListener {
        void onKeyboardNext();
    }

    public interface OnKeyboardDoneListener {
        void onKeyboardDone();
    }

    static final ViewProps<MuunTextInput> viewProps;

    static {
        final ViewProps.Builder<MuunTextInput> builder = new ViewProps.Builder<MuunTextInput>()
                .addString(android.R.attr.text, MuunTextInput::setText)
                .addString(R.attr.helperText, MuunTextInput::setHelperText)
                .addEnum(R.attr.helperTextVisibility, MuunTextInput::setHelperTextVisibilityEnum)
                .addString(android.R.attr.hint, MuunTextInput::setHint)
                .addString(android.R.attr.fontFamily, MuunTextInput::setFontFamily)
                .addBoolean(R.attr.hintEnabled, MuunTextInput::setHintEnabled)
                .addBoolean(R.attr.errorEnabled, MuunTextInput::setErrorEnabled)
                .addBoolean(android.R.attr.enabled, MuunTextInput::setEnabled)
                .addInt(android.R.attr.maxLines, MuunTextInput::setMaxLines)
                .addInt(android.R.attr.minEms, MuunTextInput::setMinEms)
                .addInt(android.R.attr.ems, MuunTextInput::setEms)
                .addInt(android.R.attr.maxEms, MuunTextInput::setMaxEms)
                .addInt(android.R.attr.maxLength, MuunTextInput::setMaxLength)
                .addInt(android.R.attr.gravity, MuunTextInput::setGravity)
                .addColorList(android.R.attr.textColor, MuunTextInput::setTextColor)
                .addEnum(android.R.attr.inputType, MuunTextInput::setInputType)
                .addRef(android.R.attr.nextFocusForward, MuunTextInput::setNextFocusForwardId)
                .addSize(android.R.attr.textSize, MuunTextInput::setTextSize)
                .addEnum(android.R.attr.textStyle, MuunTextInput::setTextStyle)
                .addEnum(android.R.attr.imeOptions, MuunTextInput::setImeOptions);

        if (UiUtils.isLollipop()) {
            builder.addFloat(android.R.attr.letterSpacing, MuunTextInput::setLetterSpacing);
        }

        viewProps = builder.build();
    }

    @State
    protected String fontFamily;

    @State
    protected int textStyle;

    @State
    protected boolean isEnabled;

    @State
    protected HelperTextVisibility helperTextVisibility;

    @BindView(R.id.muun_text_input_layout)
    protected TextInputLayout layout;

    @BindView(R.id.muun_text_input_edit_text)
    protected TextInputEditText editText;

    @BindView(R.id.muun_helper_text)
    protected TextView helperText;

    private boolean notifyChanges = true;
    private OnChangeListener changesListener;

    private OnKeyboardNextListener keyboardNextListener;

    private OnKeyboardDoneListener keyboardDoneListener;

    public MuunTextInput(Context context) {
        super(context);
    }

    public MuunTextInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunTextInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_text_input;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        // Setting defaults in here because setUp is called INSIDE the view constructor
        textStyle = Typeface.NORMAL;
        isEnabled = true;

        super.setUp(context, attrs);

        editText.setImeOptions(FIXED_IME_OPTIONS);

        isEnabled = true;
        helperTextVisibility = HelperTextVisibility.VISIBLE_WHEN_FOCUSED;
        viewProps.transfer(attrs, this);

        preloadErrorView();

        editText.addTextChangedListener(createTextWatcher());
        editText.setOnEditorActionListener(createEditorActionListener());

        updateHelperText();
        setOnFocusChangeListener((v, hasFocus) -> updateHelperText());
    }

    @Override
    protected void onRestoreInstanceState(Parcelable parcelable) {
        super.onRestoreInstanceState(parcelable);
        setEnabled(isEnabled);
    }

    private void preloadErrorView() {
        // We need to force the TextInputLayout to generate an invisible error view in order to
        // avoid jumps on the MuunTextInput layout after showing an error.
        if (!isInEditMode()) {
            layout.setErrorEnabled(true);
        }
    }

    private TextWatcher createTextWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearError();

                if (changesListener != null && notifyChanges) {
                    changesListener.onTextChanged(s.toString());
                }
            }
        };
    }

    private TextView.OnEditorActionListener createEditorActionListener() {
        return (textView, actionId, event) -> {
            if (keyboardNextListener != null && actionId == EditorInfo.IME_ACTION_NEXT) {
                keyboardNextListener.onKeyboardNext();
                return true;

            } else if (keyboardDoneListener != null && actionId == EditorInfo.IME_ACTION_DONE) {
                keyboardDoneListener.onKeyboardDone();
            }

            return false;
        };
    }

    public void setOnChangeListener(OnChangeListener changesListener) {
        this.changesListener = changesListener;
    }

    public void setOnKeyboardNextListener(OnKeyboardNextListener keyboardNextListener) {
        this.keyboardNextListener = keyboardNextListener;
    }

    public void setOnKeyboardDoneListener(OnKeyboardDoneListener keyboardDoneListener) {
        this.keyboardDoneListener = keyboardDoneListener;
    }

    public void setOnKeyListener(OnKeyListener listener) {
        editText.setOnKeyListener(listener);
    }

    /**
     * Programmatically send a key event to the underlying EditText.
     */
    public void sendKeyEvent(KeyEvent event) {
        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN:
                editText.onKeyDown(event.getKeyCode(), event);
                break;

            case KeyEvent.ACTION_UP:
                editText.onKeyUp(event.getKeyCode(), event);
                break;

            case KeyEvent.ACTION_MULTIPLE:
                editText.onKeyMultiple(event.getKeyCode(), event.getRepeatCount(), event);
                break;

            default:
        }
    }

    public Editable getText() {
        return editText.getText();
    }

    public void setText(CharSequence text) {
        editText.setText(text);
    }

    public void setTextColor(ColorStateList colors) {
        editText.setTextColor(colors);
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
        editText.setTextSize(unit, size);
    }

    public void setMaxLines(int maxlines) {
        editText.setMaxLines(maxlines);
    }

    public void setMinEms(int minems) {
        editText.setMinEms(minems);
    }

    public void setEms(int ems) {
        editText.setEms(ems);
    }

    public void setMaxEms(int maxems) {
        editText.setMaxEms(maxems);
    }

    public void setMaxLength(int maxLength) {
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
    }

    public void setInputType(int type) {
        editText.setInputType(type);
    }

    public void setImeOptions(int imeOptions) {
        editText.setImeOptions(FIXED_IME_OPTIONS | imeOptions);
    }

    /**
     * Set the gravity of the input content, helper and error messages.
     */
    public void setGravity(int gravity) {
        editText.setGravity(gravity);
        helperText.setGravity(gravity);

        updateErrorGravity();

    }

    private void updateErrorGravity() {
        // TextInputLayout always uses left-gravity for error messages. For consistency with our
        // helperText, we need to adjust that. Obviously, this is not public behavior. It's private
        // and classes are final. We need to bypass the public interface and get to the relevant
        // internal views.

        // Also obvious, the views we want to access can be missing from the tree, depending on
        // whether `setErrorEnabled` was called and with what value.

        final TextView errorView = layout.findViewById(R.id.textinput_error);
        if (errorView == null) {
            return;
        }

        final FrameLayout errorFrame = (FrameLayout) errorView.getParent();
        if (errorFrame == null) {
            return;
        }

        errorView.setGravity(helperText.getGravity());
        errorFrame.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (isEnabled() != enabled) {
            super.setEnabled(enabled);
            layout.setEnabled(enabled);
            this.isEnabled = enabled;
        }
    }

    /**
     * Set the letter spacing, on API levels that support it.
     */
    public void setLetterSpacing(float letterSpacing) {
        if (UiUtils.isLollipop()) {
            editText.setLetterSpacing(letterSpacing);
        }
    }

    public void setPasswordRevealEnabled(boolean isEnabled) {
        layout.setPasswordVisibilityToggleEnabled(isEnabled);
    }

    /**
     * Reveal or hide the contents of this MuunTextInput.
     */
    public void setPasswordRevealed(boolean shouldBeRevealed) {
        if (isPasswordRevealed() == shouldBeRevealed) {
            return;
        }

        // TextInputLayout has no way of programmatically revealing the password in its public
        // interface. Therefore, we hack:
        try {
            final Method method = layout.getClass()
                    .getDeclaredMethod("passwordVisibilityToggleRequested", boolean.class);

            method.setAccessible(true);
            method.invoke(layout, false);

        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            // We tried.
        }
    }

    public boolean isPasswordRevealed() {
        // TextInputLayout uses this same code:
        return !(editText.getTransformationMethod() instanceof PasswordTransformationMethod);
    }

    /**
     * Call `setText()` on the inner EditText, without triggering the OnEditedListener.
     */
    public void setTextSkipListener(CharSequence text) {
        this.notifyChanges = false;
        editText.setText(text);
        this.notifyChanges = true;
    }

    public void setSelection(int start, int stop) {
        editText.setSelection(start, stop);
    }

    public void setSelection(int index) {
        editText.setSelection(index);
    }

    public void disableErrorAndHelperTextTempFix() {
        setErrorEnabled(false);
        helperText.setVisibility(View.GONE);
    }

    /**
     * Set the UserFacingError to be displayed by this input.
     */
    public void setError(UserFacingError error) {
        layout.setError(error != null ? error.getLocalizedMessage() : null);
        updateHelperText();
        updateErrorGravity();
    }

    public void clearError() {
        layout.setError(null);
        updateHelperText();
    }

    public void setHint(CharSequence hint) {
        layout.setHint(hint);
        editText.setContentDescription(hint);
    }

    public CharSequence getHint() {
        return layout.getHint();
    }

    public void setHintEnabled(boolean enabled) {
        layout.setHintEnabled(enabled);
    }

    public boolean isHintEnabled() {
        return layout.isHintEnabled();
    }

    public void setErrorEnabled(boolean enabled) {
        layout.setErrorEnabled(enabled);
        updateErrorGravity();
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
        updateTypeface();
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setTextStyle(int textStyle) {
        this.textStyle = textStyle;
        updateTypeface();
    }

    public int getTextStyle() {
        return textStyle;
    }

    /**
     * Sets a helper message that will be displayed below our {@link MuunTextInput}. If the
     * {@code text} is {@code null}, the helper message will be cleared.
     *
     * @param text helper text or null
     */
    public void setHelperText(@Nullable CharSequence text) {
        helperText.setText(text);
        updateHelperText();
    }

    public void setHelperTextVisibility(HelperTextVisibility visibility) {
        this.helperTextVisibility = visibility;
        updateHelperText();
    }

    private void setHelperTextVisibilityEnum(int enumValue) {
        setHelperTextVisibility(HelperTextVisibility.values()[enumValue]);
    }

    /**
     * Add an InputFilter to the underlying EditText.
     */
    public void addFilter(InputFilter newFilter) {
        // Yes. It's 2018 and this following bit is actually happening.
        final InputFilter[] currentFilters = editText.getFilters();
        final InputFilter[] newFilters = new InputFilter[currentFilters.length + 1];

        System.arraycopy(currentFilters, 0, newFilters, 0, currentFilters.length);
        newFilters[newFilters.length - 1] = newFilter;

        editText.setFilters(newFilters);
    }

    public EditText getEditText() {
        return editText;
    }

    @Override
    public boolean hasFocus() {
        return editText.hasFocus();
    }

    @Override
    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        editText.setOnFocusChangeListener(l);
    }

    private void updateTypeface() {
        final Typeface typeface = Typeface.create(fontFamily, textStyle);

        layout.setTypeface(typeface);
        editText.setTypeface(typeface);
    }

    /**
     * Update helper text's visibility.
     * If there's an error, then hide helper text (and show error). Else, show helper text only if
     * view has focus.
     */
    private void updateHelperText() {

        final boolean shouldShow = shouldShowHelperText();

        final int oldVisibility = helperText.getVisibility();
        final int newVisibility = shouldShow ? VISIBLE : GONE;

        if (newVisibility != helperText.getVisibility()) {

            // abort any ongoing animation
            helperText.animate().cancel();

            if (oldVisibility != newVisibility && newVisibility == VISIBLE) {
                helperText
                        .animate()
                        .alpha(1f)
                        .setDuration(ANIMATION_DURATION)
                        .setStartDelay(ANIMATION_DURATION)
                        .setInterpolator(LINEAR_OUT_SLOW_IN_INTERPOLATOR)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animator) {
                                helperText.setVisibility(newVisibility);
                            }
                        })
                        .start();
            } else {
                helperText.setVisibility(newVisibility);
            }
        }
    }

    private boolean shouldShowHelperText() {
        switch (helperTextVisibility) {
            case GONE:
                return false;

            case VISIBLE_WHEN_FOCUSED:
                return layout.getError() == null && hasFocus();

            case VISIBLE:
                return layout.getError() == null;

            default:
                throw new MissingCaseError(helperTextVisibility);
        }
    }
}