package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;

import android.content.Context;
import android.util.AttributeSet;

public class FirstOnMuunView extends MuunView {

    public FirstOnMuunView(Context context) {
        super(context);
    }

    public FirstOnMuunView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FirstOnMuunView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.first_on_muun_view;
    }
}
