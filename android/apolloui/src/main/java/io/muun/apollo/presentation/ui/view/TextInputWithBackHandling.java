package io.muun.apollo.presentation.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import androidx.appcompat.widget.AppCompatEditText;

public class TextInputWithBackHandling extends AppCompatEditText {
    public interface OnBackPressed {
        void onBackPressed();
    }

    private OnBackPressed onBackPressedListener;

    public TextInputWithBackHandling(Context context) {
        super(context);
    }

    public TextInputWithBackHandling(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextInputWithBackHandling(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnBackPressedListener(OnBackPressed onBackPressedListener) {
        this.onBackPressedListener = onBackPressedListener;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (onBackPressedListener != null
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            onBackPressedListener.onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
