package io.muun.apollo.presentation.ui.utils

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View


class LinkSpan(val id: String, val reportClick: (String) -> Unit): ClickableSpan() {

    override fun onClick(textView: View) {
        reportClick(id)
    }

    override fun updateDrawState(textPaint: TextPaint) {
        super.updateDrawState(textPaint)
        textPaint.isUnderlineText = false
    }
}