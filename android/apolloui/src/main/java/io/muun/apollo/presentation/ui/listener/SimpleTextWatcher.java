package io.muun.apollo.presentation.ui.listener;

import android.text.Editable;
import android.text.TextWatcher;
import rx.functions.Action1;

public class SimpleTextWatcher implements TextWatcher {

    private final Action1<String> onChange;

    public SimpleTextWatcher(Action1<String> onChange) {
        this.onChange = onChange;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        onChange.call(s.toString());
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
}
