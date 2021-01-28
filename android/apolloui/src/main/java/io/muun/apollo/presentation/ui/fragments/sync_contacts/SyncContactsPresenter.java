package io.muun.apollo.presentation.ui.fragments.sync_contacts;

import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_MORE_INFO_TYPE;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;

import javax.inject.Inject;

@PerFragment
public class SyncContactsPresenter
        extends SingleFragmentPresenter<SyncContactsView, SyncContactsParentPresenter> {

    /**
     * Constructor.
     */
    @Inject
    public SyncContactsPresenter() {
    }

    /**
     * Call to report READ_CONTACTS permission was granted.
     */
    public void reportContactsPermissionGranted() {
        getParentPresenter().reportContactPermissionGranted();
    }

    public void reportContactsPermissionNeverAskAgain() {
        getParentPresenter().reportContactsPermissionNeverAskAgain();
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_P2P_SETUP_ENABLE_CONTACTS();
    }

    public void reportShowReadContactsInfo() {
        analytics.report(new AnalyticsEvent.S_MORE_INFO(S_MORE_INFO_TYPE.READ_CONTACTS));
    }
}
