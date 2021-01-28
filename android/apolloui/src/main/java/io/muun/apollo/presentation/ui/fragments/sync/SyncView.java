package io.muun.apollo.presentation.ui.fragments.sync;


import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface SyncView extends SingleFragmentView {

    String ARG_FROM_STEP = "from_step";

    void setLoading(boolean isLoading);

    void setIsExistingUser(boolean isExistingUser);

    void setUpPinCode(boolean canCancel);
}
