package io.muun.apollo.presentation.ui.fragments.login_authorize;

import io.muun.apollo.domain.model.SignupDraft;
import io.muun.apollo.presentation.ui.base.ParentPresenter;

public interface LoginAuthorizeParentPresenter extends ParentPresenter {

    // TODO should be parent's responsibility I think
    void reportEmailVerified();

    void cancelEmailVerification();

    SignupDraft getSignupDraft();
}
