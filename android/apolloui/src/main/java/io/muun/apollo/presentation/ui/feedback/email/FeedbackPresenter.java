package io.muun.apollo.presentation.ui.feedback.email;

import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.model.FeedbackCategory;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_SUPPORT_TYPE;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.exception.MissingCaseError;

import android.os.Bundle;
import androidx.annotation.Nullable;
import rx.Observable;

import javax.inject.Inject;

@PerActivity
public class FeedbackPresenter extends BasePresenter<FeedbackView> {

    private final UserActions userActions;

    private FeedbackCategory category;

    /**
     * Constructor.
     */
    @Inject
    public FeedbackPresenter(UserActions userActions) {
        this.userActions = userActions;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        view.setExplanation(arguments.getString(FeedbackView.FEEDBACK_EXPLANATION));

        this.category = FeedbackCategory.valueOf(
                arguments.getString(FeedbackView.FEEDBACK_CATEGORY)
        );

        setUpSubmit();
    }

    private void setUpSubmit() {
        final Observable<?> observable = userActions
                .submitFeedbackAction
                .getState()
                .doOnNext(state -> {
                    switch (state.getKind()) {
                        case EMPTY:
                            view.setLoading(false);
                            break;

                        case LOADING:
                            view.setLoading(true);
                            break;

                        case ERROR:
                            view.setLoading(false);
                            handleError(state.getError());
                            break;

                        case VALUE:
                            view.onSubmitSuccess();
                            break;

                        default:
                            break;
                    }
                });

        subscribeTo(observable);
    }

    public void submit(String feedback) {
        userActions.submitFeedbackAction.run(category, feedback);
    }

    @Nullable
    @Override
    protected AnalyticsEvent getEntryEvent() {
        switch (category) {
            case GENERAL:
                return new AnalyticsEvent.S_SUPPORT(S_SUPPORT_TYPE.FEEDBACK);

            case ERROR:
                return new AnalyticsEvent.S_SUPPORT(S_SUPPORT_TYPE.ERROR);

            default:
                throw new MissingCaseError(category);
        }
    }
}
