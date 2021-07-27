package io.muun.apollo.presentation.ui.feedback.email;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.FeedbackCategory;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.MuunTextInput;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;
import butterknife.BindView;
import butterknife.OnClick;

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

    @BindView(R.id.header)
    MuunHeader header;

    @BindView(R.id.feedback_explanation)
    TextView feedbackExplanation;

    @BindView(R.id.feedback_content)
    MuunTextInput feedbackContent;

    @BindView(R.id.submit)
    MuunButton submit;

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

        header.attachToActivity(this);
        header.showTitle(R.string.feedback_title);
        header.setNavigation(Navigation.EXIT);

        feedbackContent.setOnChangeListener(ignored -> onFeedbackContentChange());
        feedbackContent.setMaxLength(MAX_FEEDBACK_CONTENT_LENGTH);

        onFeedbackContentChange();
    }

    @Override
    public void setExplanation(@NotNull String explanation) {
        feedbackExplanation.setText(explanation);
    }

    @Override
    protected void onResume() {
        super.onResume();
        feedbackContent.requestFocusInput();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        onFeedbackContentChange();
    }

    @OnClick(R.id.submit)
    public void onSubmitClick() {
        presenter.submit(feedbackContent.getText().toString());
    }

    @Override
    public void setLoading(boolean isLoading) {
        if (! isFinishing()) {
            submit.setLoading(isLoading);
            feedbackContent.setEnabled(!isLoading);
        }
    }

    @Override
    public void onSubmitSuccess() {
        showTextToast(getString(R.string.feedback_success));
        finishActivity();
    }

    private void onFeedbackContentChange() {
        final String text = feedbackContent.getText().toString();
        submit.setEnabled(text.length() > 0 && text.length() < MAX_FEEDBACK_CONTENT_LENGTH);
    }
}
