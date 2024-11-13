package io.muun.apollo.presentation.ui.single_action;


import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.common.exception.MissingCaseError;

import javax.inject.Inject;

public class SingleActionPresenter extends BasePresenter<BaseView> {

    @Inject
    public SingleActionPresenter() {
    }

    /**
     * Executes a REQUEST_UPDATE action from SingleActionActivity.
     */
    public void requestUpdateAction() {
        logout();
        navigator.openPlayStore(getContext());
    }

    /**
     * Executes a REQUEST_RESTART action from SingleActionActivity.
     */
    public void requestRestartAction() {
        logout();
    }

    /**
     * Report the type of the action screen.
     */
    public void reportActionType(SingleActionActivity.ActionType type) {
        switch (type) {
            case REQUEST_UPDATE:
                analytics.report(new AnalyticsEvent.S_UPDATE_APP());
                break;

            case REQUEST_RESTART:
                analytics.report(new AnalyticsEvent.S_SESSION_EXPIRED());
                break;

            default:
                throw new MissingCaseError(type);
        }
    }

    @Override
    protected boolean shouldCheckClientState() {
        return false;
    }
}
