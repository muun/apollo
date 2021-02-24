package io.muun.apollo.presentation.ui.feedback.anon;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.common.Optional;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import butterknife.BindColor;
import butterknife.BindView;
import butterknife.OnClick;
import timber.log.Timber;

import javax.validation.constraints.NotNull;

public class AnonFeedbackActivity
        extends BaseActivity<AnonFeedbackPresenter>
        implements AnonFeedbackView {

    /**
     * Create an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context,
                                                Optional<String> supportId) {

        if (!supportId.isPresent()) {
            Timber.e("Missing supportId when creating AnonFeedbackActivity Intent");
        }

        return new Intent(context, AnonFeedbackActivity.class)
                .putExtra(SUPPORT_ID, supportId.orElse(null));
    }

    @BindView(R.id.header)
    MuunHeader header;

    @BindView(R.id.anon_feedback_explanation)
    HtmlTextView explanation;

    @BindView(R.id.anon_feedback_open_email_app)
    MuunButton openEmailAppButton;

    @BindColor(R.color.text_secondary_color)
    int textSecondaryColor;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.anon_feedback_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.showTitle(R.string.feedback_title);
        header.setNavigation(Navigation.BACK);

        explanation.setTextColor(textSecondaryColor); // Setting attr in xml isn't working
        explanation.setText(getExplanationBase());

        openEmailAppButton.setEnabled(presenter.hasEmailAppInstalled());
    }

    @Override
    public void setSupportId(Optional<String> maybeSupportId) {
        final String supportId = maybeSupportId.orElse(null);
        final CharSequence text;

        if (supportId != null) {
            text = TextUtils.concat(getExplanationBase(), " ", getExplanationSupportId(supportId));
        } else {
            text = getExplanationBase();
        }

        explanation.setText(text);
    }

    @OnClick(R.id.anon_feedback_open_email_app)
    public void onOpenEmailClient() {
        presenter.onOpenEmailClient();
    }

    private CharSequence getExplanationBase() {
        return new StyledStringRes(getViewContext(), R.string.anon_feedback_explanation)
                .toCharSequence();
    }

    private CharSequence getExplanationSupportId(String supportId) {
        return new StyledStringRes(getViewContext(), R.string.anon_feedback_explanation_support_id)
                .toCharSequence(supportId);
    }
}
