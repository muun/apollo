package io.muun.apollo.presentation.ui.view;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

public class HtmlTextView extends AppCompatTextView {

    public HtmlTextView(Context context) {
        super(context);
        setUp();
    }

    public HtmlTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUp();
    }

    public HtmlTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setUp();
    }

    private void setUp() {
        setHtml(getText());
        setMovementMethod(LinkMovementMethod.getInstance()); // enables ClickableSpan

        // NOTE (https://muunbtc.atlassian.net/browse/MUUN-962):
        // When HTML views with links are recreated and their state restored, older versions of
        // Android (API < 23) cause crashes because the same click handlers are re-attached, bound
        // to stale references. When clicked, those handlers cause NullPointerExceptions by trying
        // to call methods on dead Activities or Fragments.
        setSaveEnabled(false);
    }

    public void setHtml(CharSequence text) {
        super.setText(fromHtml(text));
    }

    private CharSequence fromHtml(CharSequence htmlText) {
        return Html.fromHtml(htmlText.toString());
    }
}
