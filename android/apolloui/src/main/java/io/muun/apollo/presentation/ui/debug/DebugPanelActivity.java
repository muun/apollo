package io.muun.apollo.presentation.ui.debug;

import io.muun.apollo.R;
import io.muun.apollo.data.external.HoustonConfig;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.view.MuunButton;

import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import butterknife.BindView;
import butterknife.OnClick;
import com.google.android.material.switchmaterial.SwitchMaterial;
import rx.functions.Action1;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public class DebugPanelActivity extends BaseActivity<DebugPanelPresenter> implements BaseView {

    @Inject
    HoustonConfig houstonConfig;

    @BindView(R.id.debug_text_server_address)
    TextView serverAddress;

    @BindView(R.id.debug_button_fund_wallet_onchain)
    MuunButton fundWalletOnChain;

    @BindView(R.id.debug_button_fund_wallet_offchain)
    MuunButton fundWalletOffChain;

    @BindView(R.id.debug_button_generate_blocks)
    MuunButton generateBlock;

    @BindView(R.id.debug_button_drop_last_tx)
    MuunButton dropLastTx;

    @BindView(R.id.debug_button_drop_tx)
    MuunButton dropTx;

    @BindView(R.id.debug_button_undrop_tx)
    MuunButton undropTx;

    @BindView(R.id.debug_switch_allow_multi_session)
    SwitchMaterial allowMultiSession;

    @BindView(R.id.debug_button_expire_all_other_sessions)
    MuunButton expireAllOtherSessions;

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
    protected void initializeUi() {
        super.initializeUi();

        fundWalletOnChain.setOnClickListener(v -> presenter.fundThisWalletOnChain());
        fundWalletOffChain.setOnClickListener(v -> presenter.fundThisWalletOffChain());
        generateBlock.setOnClickListener(v -> presenter.generateBlock());
        dropLastTx.setOnClickListener(v -> presenter.dropLastTxFromMempool());

        dropTx.setOnClickListener(v -> handleTxIdInput(presenter::dropTx));
        undropTx.setOnClickListener(v -> handleTxIdInput(presenter::undropTx));

        allowMultiSession.setOnCheckedChangeListener(
                (buttonView, isChecked) -> presenter.toggleMultiSessions()
        );
        expireAllOtherSessions.setOnClickListener(v -> presenter.expireAllSessions());
    }

    private void handleTxIdInput(Action1<String> handler) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Title");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            final String inputText = input.getText().toString();
            handler.call(inputText);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        serverAddress.setText(houstonConfig.getUrl());
    }

    /**
     * Handle fetch replace operations button click.
     */
    @OnClick(R.id.debug_button_fetch_operations)
    public void syncOperationsButtonClicked() {
        presenter.fetchReplaceOperations();
    }

    /**
     * Handle sync phone contacts button click.
     */
    @OnClick(R.id.debug_button_upload_phone_contacts)
    public void syncPhoneContactsButtonClicked() {
        presenter.scanReplacePhoneContacts();
    }

    /**
     * Handle sync contacts button click.
     */
    @OnClick(R.id.debug_button_fetch_contacts)
    public void syncContactsButtonClicked() {
        presenter.fetchReplaceContacts();
    }

    /**
     * Handle sync real time data button click.
     */
    @OnClick(R.id.debug_button_sync_real_time_data)
    public void syncFeeButtonClicked() {
        presenter.syncRealTimeData();
    }

    /**
     * Handle sync external address indexes button click.
     */
    @OnClick(R.id.debug_button_sync_external_addresses_indexes)
    public void syncExternalAddressesIndexesButtonClicked() {
        presenter.syncExternalAddressesIndexes();
    }

    /**
     * Handle check integrity button click.
     */
    @OnClick(R.id.debug_button_integrity_check)
    public void checkIntegrity() {
        presenter.checkIntegrity();
    }

    /**
     * Handle update FCM token button click.
     */
    @OnClick(R.id.debug_button_force_fcm_token_update)
    public void updateFcmToken() {
        presenter.updateFcmToken();
    }
}
