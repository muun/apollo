package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.databinding.FirstOnMuunViewBinding;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import kotlin.jvm.functions.Function3;

public class FirstOnMuunView extends MuunView<FirstOnMuunViewBinding> {

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
    public Function3<LayoutInflater, ViewGroup, Boolean, FirstOnMuunViewBinding> bindingInflater() {
        return FirstOnMuunViewBinding::inflate;
    }
}
