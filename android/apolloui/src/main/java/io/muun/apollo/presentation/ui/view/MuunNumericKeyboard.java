package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.MuunNumericKeyboardBinding;
import io.muun.apollo.presentation.biometrics.BiometricsController;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import kotlin.jvm.functions.Function3;

import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import javax.inject.Inject;

public class MuunNumericKeyboard extends MuunView<MuunNumericKeyboardBinding> {

    public interface OnKeyListener {
        void onNumberKey(int number);

        void onDeleteKey();

        void onBiometricsKey();
    }

    private static final String[] NUMBER_KEY_LETTERS = {
            "\u00A0", // invisible characters to occupy same space as text
            "\u00A0",
            "ABC",
            "DEF",
            "GHI",
            "JKL",
            "MNO",
            "PQRS",
            "TUV",
            "WXYZ"
    };

    public static final int MAX_WIDTH_DP = 464;
    private TextView[] numberKeys;

    private ImageView getBiometricsKey() {
        return getBinding().keyBiometrics;
    }

    private ImageView getDeleteKey() {
        return getBinding().keyDel;
    }

    private int getAlphabetCharactersColor() {
        return ContextCompat.getColor(getContext(), R.color.text_secondary_color);
    }

    @Inject
    BiometricsController biometricsController;

    private BiometricsKeyVisibilityHandler biometricsKeyVisibilityHandler;

    private OnKeyListener listener;

    public MuunNumericKeyboard(Context context) {
        super(context);
    }

    public MuunNumericKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunNumericKeyboard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public Function3<LayoutInflater, ViewGroup, Boolean, MuunNumericKeyboardBinding>
            bindingInflater() {
        return MuunNumericKeyboardBinding::inflate;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        numberKeys = new TextView[]{
                getBinding().key0,
                getBinding().key1,
                getBinding().key2,
                getBinding().key3,
                getBinding().key4,
                getBinding().key5,
                getBinding().key6,
                getBinding().key7,
                getBinding().key8,
                getBinding().key9,
        };

        if (getComponent() != null) {
            getComponent().inject(this);
        }

        for (int i = 0; i < numberKeys.length; i++) {
            final int number = i;
            final TextView numberKey = numberKeys[number];

            final CharSequence label = TextUtils.concat(
                    new RichText("" + number),
                    "\n",
                    new RichText(NUMBER_KEY_LETTERS[number])
                            .setRelativeSize(0.5f)
                            .setForegroundColor(getAlphabetCharactersColor())
            );

            numberKey.setText(label);
            numberKey.setOnClickListener(ignored -> onNumberKeyClicked(number));
        }

        getDeleteKey().setOnClickListener(ignored -> onDeleteKeyClicked());
    }

    public void setListener(OnKeyListener listener) {
        this.listener = listener;
    }

    /**
     * Enable or disable the keyboard.
     */
    public void setEnabled(boolean isEnabled) {
        for (View key : numberKeys) {
            key.setEnabled(isEnabled);
        }

        setDeleteEnabled(isEnabled);
    }

    /**
     * Set whether the delete key can be pressed.
     */
    public void setDeleteEnabled(boolean enabled) {
        UiUtils.setTint(getDeleteKey(), enabled ? R.color.icon_color : R.color.disabled_color);
        getDeleteKey().setEnabled(enabled);
    }

    private void onNumberKeyClicked(int number) {
        if (listener != null && numberKeys[number].isEnabled()) {
            listener.onNumberKey(number);
        }
    }

    private void onBiometricsKeyClicked() {
        if (listener != null) {
            listener.onBiometricsKey();
        }
    }

    private void onDeleteKeyClicked() {
        if (listener != null && getDeleteKey().isEnabled()) {
            listener.onDeleteKey();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        // Do not allow MuunNumericKeyboard to take more width than MAX_WIDTH_DP.
        // This is mostly intended for landscape and tablets.

        final Resources resources = getResources();

        final int maxWidthPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                MAX_WIDTH_DP,
                resources.getDisplayMetrics()
        );

        final int specWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int modeWidth = MeasureSpec.getMode(widthMeasureSpec);

        final int minMeasuredWidth = MeasureSpec.makeMeasureSpec(
                Math.min(specWidth, maxWidthPx),
                modeWidth
        );

        super.onMeasure(minMeasuredWidth, heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        biometricsKeyVisibilityHandler =
                new BiometricsKeyVisibilityHandler(ViewTreeLifecycleOwner.get(this));
        biometricsKeyVisibilityHandler.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (biometricsKeyVisibilityHandler != null) {
            biometricsKeyVisibilityHandler.detach();
            biometricsKeyVisibilityHandler = null;
        }
    }

    private class BiometricsKeyVisibilityHandler implements DefaultLifecycleObserver {
        private final WeakReference<LifecycleOwner> lifecycleOwner;

        BiometricsKeyVisibilityHandler(LifecycleOwner lifecycleOwner) {
            this.lifecycleOwner = new WeakReference<>(lifecycleOwner);
        }

        @Override
        public void onResume(@NonNull LifecycleOwner owner) {
            if (biometricsController.hasUserOptedInBiometrics()
                    && biometricsController.getAuthenticationStatus().getCanAuthenticate()) {
                getBiometricsKey().setOnClickListener(ignored -> onBiometricsKeyClicked());
                getBiometricsKey().setVisibility(View.VISIBLE);
            } else {
                getBiometricsKey().setVisibility(View.INVISIBLE);
            }
        }

        public void attach() {
            final LifecycleOwner lifecycleOwner = this.lifecycleOwner.get();
            if (lifecycleOwner != null) {
                lifecycleOwner.getLifecycle().addObserver(this);
            }
        }

        public void detach() {
            final LifecycleOwner lifecycleOwner = this.lifecycleOwner.get();
            if (lifecycleOwner != null) {
                lifecycleOwner.getLifecycle().removeObserver(this);
            }
        }
    }
}
