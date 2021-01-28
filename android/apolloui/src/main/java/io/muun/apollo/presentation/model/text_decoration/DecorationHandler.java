package io.muun.apollo.presentation.model.text_decoration;

public interface DecorationHandler {

    void setTextSize(int unit, float size);

    int getSelectionStart();

    void setSelection(int pos);

    void setText(CharSequence text);

    int length();

}
