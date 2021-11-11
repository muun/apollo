package io.muun.apollo.presentation.ui.settings.edit_username;

import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.presentation.ui.base.BaseView;

interface EditUsernameView extends BaseView {

    void setUsername(User user);

    void setFirstNameError(UserFacingError error);

    void setLastNameError(UserFacingError error);

    void setLoading(boolean loading);
}
