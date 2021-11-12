package io.muun.apollo.presentation.ui.fragments.profile;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.apollo.presentation.ui.base.ParentPresenter;
import io.muun.common.Optional;

import android.net.Uri;
import rx.Observable;

public interface ProfileParentPresenter extends ParentPresenter {

    /**
     * Submit a public profile as part of the P2P setup.
     */
    void submitProfile(String firstName, String lastName, Optional<Uri> pictureUri);

    /**
     * Get an Observable for the SubmitProfile async action.
     */
    Observable<ActionState<UserProfile>> watchSubmitProfile();

}
