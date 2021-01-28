package io.muun.apollo.presentation.model.text_decoration;

public interface DecorationTransformation {

    void setTarget(DecorationHandler target);

    void beforeTextChanged(CharSequence s, int start, int count, int after);

    void afterTextChanged(StringBuilder s);

}