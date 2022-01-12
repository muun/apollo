package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.view.MuunPinIndicator.PinIndicatorState;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.StringRes;
import butterknife.BindString;
import butterknife.BindView;
import icepick.State;
import rx.functions.Action0;

import javax.annotation.Nullable;

public class MuunPinInput extends MuunView {

    public interface OnPinEnteredListener {
        void onPinEntered(String pin);
    }

    static final ViewProps<MuunPinInput> viewProps = new ViewProps.Builder<MuunPinInput>()
            .addRefJava(R.attr.title, MuunPinInput::setTitle)
            .addRefJava(R.attr.description, MuunPinInput::setDescription)
            .build();

    public static final int PIN_LENGTH = 4; // NOTE: hard-coded in MuunPinIndicator XML as well

    @BindView(R.id.pin_input_indicator)
    MuunPinIndicator indicator;

    @BindView(R.id.set_up_pin_title)
    TextView title;

    @BindView(R.id.set_up_pin_description)
    TextView description;

    @BindView(R.id.error_message)
    TextView errorMessage;

    @BindView(R.id.pin_input_keyboard)
    MuunNumericKeyboard keyboard;

    @BindString(R.string.pin_attempts_left_zero)
    String attemptsLeftZero;

    @BindString(R.string.pin_attempts_left_one)
    String attemptsLeftOne;

    @BindString(R.string.pin_attempts_left_many)
    String attemptsLeftMany;

    @State
    String content = "";

    private OnPinEnteredListener listener;

    public MuunPinInput(Context context) {
        super(context);
    }

    public MuunPinInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunPinInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_pin_input;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        viewProps.transfer(attrs, this);

        keyboard.setListener(new KeyboardListener());
        keyboard.setDeleteEnabled(false);
    }

    public void setListener(OnPinEnteredListener listener) {
        this.listener = listener;
    }

    /**
     * Reset the content and indicators of this input.
     */
    public void clear() {
        content = "";
        errorMessage.setVisibility(View.INVISIBLE);
        updateAfterChange();
    }

    /**
     * Set the remaining PIN attempts.
     */
    public void setRemainingAttempts(int attempts) {
        errorMessage.setVisibility(View.VISIBLE);

        if (attempts > 1) {
            errorMessage.setText(String.format(attemptsLeftMany, attempts));
        } else if (attempts == 1) {
            errorMessage.setText(attemptsLeftOne);
        } else {
            errorMessage.setText(attemptsLeftZero);
        }
    }

    public void setRemainingAttemptsVisible(boolean isVisible) {
        errorMessage.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Sets the error message, i.e.: "pin does not match"
     * @param text error message resource
     */
    public void setErrorMessage(@StringRes int text) {
        errorMessage.setVisibility(View.VISIBLE);
        errorMessage.setText(text);
    }

    /**
     * Sets the title resource, i.e.: "Enter your PIN".
     * @param resId title resource.
     */
    public void setTitle(@StringRes int resId) {
        title.setText(resId);
    }

    /**
     * Sets the description resource, i.e.: "You will use this code to unlock muun".
     * @param resId title resource.
     */
    public void setDescription(@StringRes int resId) {
        description.setVisibility(VISIBLE);
        description.setText(resId);
    }

    /**
     * Hides the description (subtitle).
     */
    public void hideDescription() {
        description.setVisibility(GONE);
    }

    /**
     * Briefly indicate error, disabling and clearing the input, invoking a callback when done.
     */
    public void flashError(@Nullable Action0 callback) {
        indicator.flashState(PinIndicatorState.ERROR, () -> {
            clear();

            if (callback != null) {
                callback.call();
            }
        });
    }

    /**
     * Indicate success, disabling further input.
     */
    public void setSuccess() {
        indicator.setState(PinIndicatorState.SUCCESS);
    }

    private void updateAfterChange() {
        final int inputLength = content.length();

        indicator.setProgress(inputLength);

        keyboard.setEnabled(inputLength < PIN_LENGTH); // allow typing when incomplete
        keyboard.setDeleteEnabled(inputLength > 0); // allow deletion when empty

        if (inputLength == PIN_LENGTH && listener != null) {
            listener.onPinEntered(content);
        }
    }

    private class KeyboardListener implements MuunNumericKeyboard.OnKeyListener {
        @Override
        public void onNumberKey(int number) {
            if (content.length() < PIN_LENGTH) {
                content += number;
                updateAfterChange();
            }
        }

        @Override
        public void onDeleteKey() {
            if (content.length() > 0) {
                content = content.substring(0, content.length() - 1);
                updateAfterChange();
            }
        }
    }
}
