package io.muun.apollo.presentation.ui.debug;

import io.muun.apollo.R;
import io.muun.apollo.data.external.HoustonConfig;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.base.BaseView;

import android.content.Context;
import android.content.Intent;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.OnClick;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public class DebugPanelActivity extends BaseActivity<DebugPanelPresenter> implements BaseView {

    @Inject
    HoustonConfig houstonConfig;

    @BindView(R.id.debug_text_server_address)
    TextView serverAddress;

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, DebugPanelActivity.class);
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.debug_activity;
    }

    @Override
    protected void onResume() {
        super.onResume();
        serverAddress.setText(houstonConfig.getUrl());
    }

    @OnClick(R.id.debug_button_fetch_operations)
    public void syncOperationsButtonClicked() {
        presenter.fetchReplaceOperations();
    }

    @OnClick(R.id.debug_button_upload_phone_contacts)
    public void syncPhoneContactsButtonClicked() {
        presenter.scanReplacePhoneContacts();
    }

    @OnClick(R.id.debug_button_fetch_contacts)
    public void syncContactsButtonClicked() {
        presenter.fetchReplaceContacts();
    }

    @OnClick(R.id.debug_button_sync_real_time_data)
    public void syncFeeButtonClicked() {
        presenter.syncRealTimeData();
    }

    @OnClick(R.id.debug_button_sync_external_addresses_indexes)
    public void syncExternalAddressesIndexesButtonClicked() {
        presenter.syncExternalAddressesIndexes();
    }

    @OnClick(R.id.debug_button_integrity_check)
    public void checkIntegrity() {
        presenter.checkIntegrity();
    }

    @OnClick(R.id.debug_button_force_fcm_token_update)
    public void updateFcmToken() {
        presenter.updateFcmToken();
    }
}
