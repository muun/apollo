package io.muun.apollo.presentation.ui.utils

import android.widget.EditText
import androidx.core.widget.doAfterTextChanged

fun EditText.setTextIfChanged(value: String) {
    if (text.toString() == value) {
        return
    }

    setText(value)
}

fun EditText.doAfterTextAsStringChanged(afterTextChanged: (String) -> Unit) {
    doAfterTextChanged { it?.toString().orEmpty().let(afterTextChanged) }
}
