package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.MuunPinInputBinding;
import io.muun.apollo.presentation.ui.view.MuunPinIndicator.PinIndicatorState;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.StringRes;
import androidx.viewbinding.ViewBinding;
import icepick.State;
import kotlin.jvm.functions.Function1;
import rx.functions.Action0;

import javax.annotation.Nullable;

public class MuunPinInput extends MuunView {

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

    private MuunPinInputBinding binding;

    @Override
    public Function1<View, ViewBinding> viewBinder() {
        return MuunPinInputBinding::bind;
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
    protected int getLayoutResource() {
        return R.layout.muun_pin_input;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        binding = (MuunPinInputBinding) getBinding();
        viewProps.transfer(attrs, this);

        binding.pinInputKeyboard.setListener(new KeyboardListener());
        binding.pinInputKeyboard.setDeleteEnabled(false);
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
        binding.errorMessage.setVisibility(View.INVISIBLE);
        updateAfterChange();
    }

    /**
     * Set the remaining PIN attempts.
     */
    public void setRemainingAttempts(int attempts) {
        binding.errorMessage.setVisibility(View.VISIBLE);

        if (attempts > 1) {
            binding.errorMessage.setText(getContext().getString(
                    R.string.pin_attempts_left_many,
                    attempts
            ));
        } else if (attempts == 1) {
            binding.errorMessage.setText(R.string.pin_attempts_left_one);
        } else {
            binding.errorMessage.setText(R.string.pin_attempts_left_zero);
        }
    }

    public void setRemainingAttemptsVisible(boolean isVisible) {
        binding.errorMessage.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Sets the error message, i.e.: "pin does not match"
     * @param text error message resource
     */
    public void setErrorMessage(@StringRes int text) {
        binding.errorMessage.setVisibility(View.VISIBLE);
        binding.errorMessage.setText(text);
    }

    /**
     * Sets the title resource, i.e.: "Enter your PIN".
     * @param resId title resource.
     */
    public void setTitle(@StringRes int resId) {
        binding.setUpPinTitle.setText(resId);
    }

    /**
     * Sets the description resource, i.e.: "You will use this code to unlock muun".
     * @param resId title resource.
     */
    public void setDescription(@StringRes int resId) {
        binding.setUpPinDescription.setVisibility(VISIBLE);
        binding.setUpPinDescription.setText(resId);
    }

    /**
     * Hides the description (subtitle).
     */
    public void hideDescription() {
        binding.setUpPinDescription.setVisibility(GONE);
    }

    /**
     * Briefly indicate error, disabling and clearing the input, invoking a callback when done.
     */
    public void flashError(@Nullable Action0 callback) {
        binding.pinInputIndicator.flashState(PinIndicatorState.ERROR, () -> {
            clear();

            if (callback != null) {
                callback.call();
            }
        });
    }

    public void setPinLength(int length) {
        binding.pinInputIndicator.setLength(length);
    }

    /**
     * Indicate success, disabling further input.
     */
    public void setSuccess() {
        binding.pinInputIndicator.setState(PinIndicatorState.SUCCESS);
    }

    private void updateAfterChange() {
        final int inputLength = content.length();

        binding.pinInputIndicator.setProgress(inputLength);

        binding.pinInputKeyboard.setEnabled(inputLength
                < binding.pinInputIndicator.getLength()); // allow typing when incomplete
        binding.pinInputKeyboard.setDeleteEnabled(inputLength > 0); // allow deletion when empty

        if (inputLength == binding.pinInputIndicator.getLength() && listener != null) {
            listener.onPinEntered(content);
        }
    }

    private class KeyboardListener implements MuunNumericKeyboard.OnKeyListener {
        @Override
        public void onNumberKey(int number) {
            if (content.length() < binding.pinInputIndicator.getLength()) {
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
