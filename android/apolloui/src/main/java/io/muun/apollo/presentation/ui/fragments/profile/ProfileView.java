package io.muun.apollo.presentation.ui.fragments.profile;

import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface ProfileView extends SingleFragmentView {

    void setFirstNameError(UserFacingError error);

    void setLastNameError(UserFacingError error);

    void setLoading(boolean isLoading);
}
