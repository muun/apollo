package io.muun.apollo.presentation.ui.fragments.profile;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.apollo.presentation.ui.base.ParentPresenter;
import io.muun.common.Optional;

import android.net.Uri;
import rx.Observable;

public interface ProfileParentPresenter extends ParentPresenter {

    void submitProfile(String firstName, String lastName, Optional<Uri> pictureUri);

    Observable<ActionState<UserProfile>> watchSubmitProfile();

}
