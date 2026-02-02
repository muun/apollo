package io.muun.apollo.presentation.ui.feedback.email;

import io.muun.apollo.R;
import io.muun.apollo.databinding.FeedbackActivityBinding;
import io.muun.apollo.domain.model.FeedbackCategory;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import javax.validation.constraints.NotNull;

public class FeedbackActivity extends BaseActivity<FeedbackPresenter> implements FeedbackView {

    /**
     * Create an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context,
                                                @NotNull FeedbackCategory category,
                                                @NotNull String explanation) {

        return new Intent(context, FeedbackActivity.class)
                .putExtra(FEEDBACK_CATEGORY, category.name())
                .putExtra(FEEDBACK_EXPLANATION, explanation);
    }

    // NOTE: keep this value at or below Houston's DB limit.
    private static final int MAX_FEEDBACK_CONTENT_LENGTH = 10000;

    private FeedbackActivityBinding binding() {
        return (FeedbackActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return FeedbackActivityBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.feedback_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();
        final var binding = binding();

        binding.header.attachToActivity(this);
        binding.header.showTitle(R.string.feedback_title);
        binding.header.setNavigation(Navigation.EXIT);

        binding.feedbackContent.setOnChangeListener(this, ignored -> onFeedbackContentChange());
        binding.feedbackContent.setMaxLength(MAX_FEEDBACK_CONTENT_LENGTH);

        onFeedbackContentChange();

        binding.submit.setOnClickListener(v ->
                presenter.submit(binding.feedbackContent.getText().toString())
        );
    }

    @Override
    public void setExplanation(@NotNull String explanation) {
        binding().feedbackExplanation.setText(explanation);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding().feedbackContent.requestFocusInput();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        onFeedbackContentChange();
    }

    @Override
    public void setLoading(boolean isLoading) {
        if (!isFinishing()) {
            final var binding = binding();
            binding.submit.setLoading(isLoading);
            binding.feedbackContent.setEnabled(!isLoading);
        }
    }

    @Override
    public void onSubmitSuccess() {
        showTextToast(getString(R.string.feedback_success));
        finishActivity();
    }

    private void onFeedbackContentChange() {
        final var binding = binding();
        final String text = binding.feedbackContent.getText().toString();
        binding.submit.setEnabled(!text.isEmpty() && text.length() < MAX_FEEDBACK_CONTENT_LENGTH);
    }
}
