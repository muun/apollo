package io.muun.apollo.presentation.ui.settings.edit_password;

import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface ChangePasswordView extends SingleFragmentView {

    void setPasswordError(UserFacingError error);

}
