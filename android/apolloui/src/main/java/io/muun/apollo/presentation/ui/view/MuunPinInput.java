package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.MuunPinInputBinding;
import io.muun.apollo.presentation.ui.view.MuunPinIndicator.PinIndicatorState;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.StringRes;
import icepick.State;
import kotlin.jvm.functions.Function3;
import rx.functions.Action0;

import javax.annotation.Nullable;

public class MuunPinInput extends MuunView<MuunPinInputBinding> {

    public interface OnPinEnteredListener {
        void onPinEntered(String pin);
    }

    public interface OnBiometricsRequestedListener {
        void onBiometricsRequested();
    }

    static final ViewProps<MuunPinInput> viewProps = new ViewProps.Builder<MuunPinInput>()
            .addRefJava(R.attr.title, MuunPinInput::setTitle)
            .addRefJava(R.attr.description, MuunPinInput::setDescription)
            .addInt(R.attr.pinLength, MuunPinInput::setPinLength)
            .build();

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, MuunPinInputBinding> bindingInflater() {
        return MuunPinInputBinding::inflate;
    }

    @State
    String content = "";

    private OnPinEnteredListener listener;
    private OnBiometricsRequestedListener biometricsRequestedListener;

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
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        viewProps.transfer(attrs, this);

        getBinding().pinInputKeyboard.setListener(new KeyboardListener());
        getBinding().pinInputKeyboard.setDeleteEnabled(false);
    }

    public void setListener(OnPinEnteredListener listener) {
        this.listener = listener;
    }

    public void setBiometricsRequestedListener(OnBiometricsRequestedListener listener) {
        this.biometricsRequestedListener = listener;
    }

    /**
     * Reset the content and indicators of this input.
     */
    public void clear() {
        content = "";
        getBinding().errorMessage.setVisibility(View.INVISIBLE);
        updateAfterChange();
    }

    /**
     * Set the remaining PIN attempts.
     */
    public void setRemainingAttempts(int attempts) {
        getBinding().errorMessage.setVisibility(View.VISIBLE);

        if (attempts > 1) {
            getBinding().errorMessage.setText(getContext().getString(
                    R.string.pin_attempts_left_many,
                    attempts
            ));
        } else if (attempts == 1) {
            getBinding().errorMessage.setText(R.string.pin_attempts_left_one);
        } else {
            getBinding().errorMessage.setText(R.string.pin_attempts_left_zero);
        }
    }

    public void setRemainingAttemptsVisible(boolean isVisible) {
        getBinding().errorMessage.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Sets the error message, i.e.: "pin does not match"
     * @param text error message resource
     */
    public void setErrorMessage(@StringRes int text) {
        getBinding().errorMessage.setVisibility(View.VISIBLE);
        getBinding().errorMessage.setText(text);
    }

    /**
     * Sets the title resource, i.e.: "Enter your PIN".
     * @param resId title resource.
     */
    public void setTitle(@StringRes int resId) {
        getBinding().setUpPinTitle.setText(resId);
    }

    /**
     * Sets the description resource, i.e.: "You will use this code to unlock muun".
     * @param resId title resource.
     */
    public void setDescription(@StringRes int resId) {
        getBinding().setUpPinDescription.setVisibility(VISIBLE);
        getBinding().setUpPinDescription.setText(resId);
    }

    /**
     * Hides the description (subtitle).
     */
    public void hideDescription() {
        getBinding().setUpPinDescription.setVisibility(GONE);
    }

    /**
     * Briefly indicate error, disabling and clearing the input, invoking a callback when done.
     */
    public void flashError(@Nullable Action0 callback) {
        getBinding().pinInputIndicator.flashState(PinIndicatorState.ERROR, () -> {
            clear();

            if (callback != null) {
                callback.call();
            }
        });
    }

    public void setPinLength(int length) {
        getBinding().pinInputIndicator.setLength(length);
    }

    /**
     * Indicate success, disabling further input.
     */
    public void setSuccess() {
        getBinding().pinInputIndicator.setState(PinIndicatorState.SUCCESS);
    }

    private void updateAfterChange() {
        final int inputLength = content.length();

        getBinding().pinInputIndicator.setProgress(inputLength);

        getBinding().pinInputKeyboard.setEnabled(inputLength
                < getBinding().pinInputIndicator.getLength()); // allow typing when incomplete
        // allow deletion when empty
        getBinding().pinInputKeyboard.setDeleteEnabled(inputLength > 0);

        if (inputLength == getBinding().pinInputIndicator.getLength() && listener != null) {
            listener.onPinEntered(content);
        }
    }

    private class KeyboardListener implements MuunNumericKeyboard.OnKeyListener {
        @Override
        public void onNumberKey(int number) {
            if (content.length() < getBinding().pinInputIndicator.getLength()) {
                content += number;
                updateAfterChange();
            }
        }

        @Override
        public void onDeleteKey() {
            if (!content.isEmpty()) {
                content = content.substring(0, content.length() - 1);
                updateAfterChange();
            }
        }

        @Override
        public void onBiometricsKey() {
            if (biometricsRequestedListener != null) {
                biometricsRequestedListener.onBiometricsRequested();
            }
        }
    }
}
