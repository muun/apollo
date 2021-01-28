package io.muun.apollo.presentation.ui.fragments.sync_contacts;

import io.muun.apollo.presentation.ui.base.ParentPresenter;

public interface SyncContactsParentPresenter extends ParentPresenter {

    void reportContactPermissionGranted();

    void reportContactsPermissionNeverAskAgain();
}
