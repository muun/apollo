package io.muun.apollo.presentation.model.text_decoration;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

/**
 * This TextWatcher accepts instances of `DecorationTransformation` which will be executed
 * in order without triggering edition loops.
 */
public class TextDecorator<T extends EditText & DecorationHandler> implements TextWatcher {

    private final T target;
    private final DecorationTransformation[] decorations;
    private Runnable afterChangeListener;

    /**
     * Constructor.
     */
    public TextDecorator(T target, DecorationTransformation[] transformations) {
        this.target = target;
        this.decorations = transformations;
        for (DecorationTransformation decoration : decorations) {
            decoration.setTarget(target);
        }
        target.addTextChangedListener(this);
    }

    /**
     * Set action to be executed after all the decoration transformations.
     * @param action Runnable
     */
    public void setAfterChangeListener(Runnable action) {
        this.afterChangeListener = action;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        for (DecorationTransformation decoration : decorations) {
            decoration.beforeTextChanged(s, start, count, after);
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // do nothing
    }

    @Override
    public void afterTextChanged(Editable s) {
        target.removeTextChangedListener(this);

        for (DecorationTransformation decoration : decorations) {
            decoration.afterTextChanged(new StringBuilder(s));
        }

        target.addTextChangedListener(this);

        if (afterChangeListener != null) {
            afterChangeListener.run();
        }
    }
}