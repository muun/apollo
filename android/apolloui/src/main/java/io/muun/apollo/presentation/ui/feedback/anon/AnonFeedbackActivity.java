package io.muun.apollo.presentation.ui.feedback.anon;

import io.muun.apollo.R;
import io.muun.apollo.databinding.AnonFeedbackActivityBinding;
import io.muun.apollo.presentation.app.Email;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.common.Optional;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;
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

    private AnonFeedbackActivityBinding binding() {
        return (AnonFeedbackActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return AnonFeedbackActivityBinding::inflate;
    }

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
        final var binding = binding();

        binding.header.attachToActivity(this);
        binding.header.showTitle(R.string.feedback_title);
        binding.header.setNavigation(Navigation.BACK);

        binding.anonFeedbackExplanation.setTextColor(
                getResources().getColor(R.color.text_secondary_color)
        ); // Setting attr in xml isn't working
        binding.anonFeedbackExplanation.setText(getExplanationBase());

        binding.anonFeedbackOpenEmailApp.setEnabled(Email.INSTANCE.hasEmailAppInstalled(this));
        binding.anonFeedbackOpenEmailApp.setOnClickListener(v -> presenter.openEmailClient());
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

        binding().anonFeedbackExplanation.setText(text);
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
