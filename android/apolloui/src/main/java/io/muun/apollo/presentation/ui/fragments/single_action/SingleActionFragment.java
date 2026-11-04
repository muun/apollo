package io.muun.apollo.presentation.ui.fragments.single_action;

import io.muun.apollo.databinding.SingleActionFragmentBinding;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.utils.UiUtils;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

public abstract class SingleActionFragment<PresenterT extends SingleFragmentPresenter>
        extends SingleFragment<PresenterT> {

    private SingleActionFragmentBinding binding() {
        return (SingleActionFragmentBinding) getBinding();
    }

    protected TextView descriptionView() {
        return binding().singleActionDescription;
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return SingleActionFragmentBinding::inflate;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true); // We need this as we store an Action0 as fragment state
    }

    @CallSuper
    @Override
    protected void initializeUi(View view) {
        final int imageRes = getImageRes();
        final String message = getTitle();
        final CharSequence descriptionText = getDescription();
        final int actionLabelRes = getActionLabelRes();

        if (imageRes != 0) {
            binding().singleActionImage.setImageResource(imageRes);

            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    getImageWidth(),
                    getImageHeight()
            );

            params.bottomMargin = UiUtils.dpToPx(requireContext(), 24);

            binding().singleActionImage.setLayoutParams(params);
            binding().singleActionImage.setVisibility(View.VISIBLE);
        }

        binding().singleActionMessage.setText(message);

        if (descriptionText != null) {
            binding().singleActionDescription.setText(descriptionText);
            binding().singleActionDescription.setVisibility(View.VISIBLE);
        }

        binding().singleActionAction.setText(actionLabelRes);
        binding().singleActionAction.setOnClickListener(ignored -> onActionClick());
    }

    @DrawableRes
    protected int getImageRes() {
        return 0;
    }

    protected int getImageWidth() {
        return UiUtils.dpToPx(requireContext(), 96);
    }

    protected int getImageHeight() {
        return UiUtils.dpToPx(requireContext(), 96);
    }

    protected abstract String getTitle();

    protected CharSequence getDescription() {
        return null;
    }

    @StringRes
    protected abstract int getActionLabelRes();

    protected void onActionClick() {
        finishActivity();
    }
}
