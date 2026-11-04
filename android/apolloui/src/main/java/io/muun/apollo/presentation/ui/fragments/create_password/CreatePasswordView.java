package io.muun.apollo.presentation.ui.fragments.create_password;


import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

public interface CreatePasswordView extends SingleFragmentView {

    void setLoading(boolean isLoading);

    void setPasswordError(@Nullable UserFacingError error);

    void setConfirmPasswordError(@NotNull UserFacingError error);
}
