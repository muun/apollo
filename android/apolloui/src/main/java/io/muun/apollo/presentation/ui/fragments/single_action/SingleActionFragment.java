package io.muun.apollo.presentation.ui.fragments.single_action;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.MuunButton;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import butterknife.BindView;


public abstract class SingleActionFragment<PresenterT extends SingleFragmentPresenter>
        extends SingleFragment<PresenterT> {

    @BindView(R.id.single_action_image)
    ImageView image;

    @BindView(R.id.single_action_message)
    TextView messageView;

    @BindView(R.id.single_action_description)
    protected TextView description;

    @BindView(R.id.single_action_action)
    MuunButton actionButton;

    @Override
    protected int getLayoutResource() {
        return R.layout.single_action_fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true); // We need this as we store an Action0 as fragment state
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        final int imageRes = getImageRes();
        final String message = getTitle();
        final CharSequence descriptionText = getDescription();
        final int actionLabelRes = getActionLabelRes();

        if (imageRes != 0) {
            image.setImageResource(imageRes);

            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    getImageWidth(),
                    getImageHeight()
            );

            params.bottomMargin = UiUtils.dpToPx(getContext(), 24);

            image.setLayoutParams(params);
            image.setVisibility(View.VISIBLE);
        }

        messageView.setText(message);

        if (descriptionText != null) {
            description.setText(descriptionText);
            description.setVisibility(View.VISIBLE);
        }

        actionButton.setText(actionLabelRes);
        actionButton.setOnClickListener(ignored -> onActionClick());
    }

    @DrawableRes
    protected int getImageRes() {
        return 0;
    }

    protected int getImageWidth() {
        return UiUtils.dpToPx(getContext(), 96);
    }

    protected int getImageHeight() {
        return UiUtils.dpToPx(getContext(), 96);
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
