package io.muun.apollo.presentation.ui.view;


import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import androidx.annotation.NonNull;

import javax.validation.constraints.NotNull;

public class RichText extends SpannableStringBuilder {

    private static final int FLAGS = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;

    public interface OnClickListener {
        void onClick();
    }

    public RichText() {
        super();
    }

    public RichText(@NotNull CharSequence text) {
        super(text);
    }

    public RichText(@NotNull Character ch) {
        this(ch.toString());
    }

    public RichText setLink(@NotNull OnClickListener listener) {
        setSpan(new LinkSpan(listener));
        return this;
    }

    public RichText setRelativeSize(float factor) {
        setSpan(new RelativeSizeSpan(factor));
        return this;
    }

    public RichText setForegroundColor(int color) {
        setSpan(new ForegroundColorSpan(color));
        return this;
    }

    public RichText setBackgroundColor(int color) {
        setSpan(new BackgroundColorSpan(color));
        return this;
    }

    public RichText setBold() {
        setSpan(new StyleSpan(Typeface.BOLD));
        return this;
    }

    public RichText setUnderline() {
        setSpan(new UnderlineSpan());
        return this;
    }

    public RichText setItalic() {
        setSpan(new StyleSpan(Typeface.ITALIC));
        return this;
    }

    public RichText setFontFamily(String fontFamily) {
        setSpan(new TypefaceSpan(fontFamily));
        return this;
    }

    public RichText concat(CharSequence other) {
        return new RichText(TextUtils.concat(this, other));
    }

    private void setSpan(Object span) {
        setSpan(span, 0, length(), FLAGS);
    }

    private static class LinkSpan extends ClickableSpan {
        private final OnClickListener clickListener;

        public LinkSpan(@NotNull OnClickListener clickListener) {
            this.clickListener = clickListener;
        }

        @Override
        public void onClick(@NonNull View textView) {
            clickListener.onClick();
        }

        @Override
        public void updateDrawState(@NonNull TextPaint textPaint) {
            super.updateDrawState(textPaint);
            textPaint.setUnderlineText(false);
        }
    }
}
