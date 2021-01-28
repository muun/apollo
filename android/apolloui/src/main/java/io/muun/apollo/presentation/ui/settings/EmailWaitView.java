package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.presentation.ui.base.SingleFragmentView;

interface EmailWaitView extends SingleFragmentView {

    void handleInvalidLinkError();

    void handleExpiredLinkError();
}
