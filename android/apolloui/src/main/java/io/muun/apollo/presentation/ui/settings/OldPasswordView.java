package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

interface OldPasswordView extends SingleFragmentView {

    void setPasswordError(UserFacingError error);
}
