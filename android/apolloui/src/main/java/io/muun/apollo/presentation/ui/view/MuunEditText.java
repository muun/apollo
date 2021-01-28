package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.presentation.model.text_decoration.DecorationHandler;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatEditText;

public class MuunEditText extends AppCompatEditText implements DecorationHandler {

    public MuunEditText(Context context) {
        super(context);
    }

    public MuunEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
