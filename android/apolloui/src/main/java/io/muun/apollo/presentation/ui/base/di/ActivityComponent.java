package io.muun.apollo.presentation.ui.base.di;

import io.muun.apollo.presentation.ui.activity.operations.OperationsActivity;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivityImpl;
import io.muun.apollo.presentation.ui.debug.DebugPanelActivity;
import io.muun.apollo.presentation.ui.export_keys.EmergencyKitActivity;
import io.muun.apollo.presentation.ui.feedback.anon.AnonFeedbackActivity;
import io.muun.apollo.presentation.ui.feedback.email.FeedbackActivity;
import io.muun.apollo.presentation.ui.high_fees.HighFeesExplanationActivity;
import io.muun.apollo.presentation.ui.home.HomeActivity;
import io.muun.apollo.presentation.ui.launcher.LauncherActivity;
import io.muun.apollo.presentation.ui.lnurl.intro.LnUrlIntroActivity;
import io.muun.apollo.presentation.ui.lnurl.withdraw.LnUrlWithdrawActivity;
import io.muun.apollo.presentation.ui.lnurl.withdraw.confirm.LnUrlWithdrawConfirmActivity;
import io.muun.apollo.presentation.ui.migration.MigrationActivity;
import io.muun.apollo.presentation.ui.new_operation.NewOperationActivity;
import io.muun.apollo.presentation.ui.operation_detail.OperationDetailActivity;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity;
import io.muun.apollo.presentation.ui.recovery_tool.RecoveryToolActivity;
import io.muun.apollo.presentation.ui.scan_qr.ScanQrActivity;
import io.muun.apollo.presentation.ui.security_logout.SecurityLogoutActivity;
import io.muun.apollo.presentation.ui.select_amount.SelectAmountActivity;
import io.muun.apollo.presentation.ui.select_bitcoin_unit.SelectBitcoinUnitActivity;
import io.muun.apollo.presentation.ui.select_country.SelectCountryActivity;
import io.muun.apollo.presentation.ui.select_currency.SelectCurrencyActivity;
import io.muun.apollo.presentation.ui.select_night_mode.SelectNightModeActivity;
import io.muun.apollo.presentation.ui.send.SendActivity;
import io.muun.apollo.presentation.ui.settings.edit_password.EditPasswordActivity;
import io.muun.apollo.presentation.ui.settings.edit_username.EditUsernameActivity;
import io.muun.apollo.presentation.ui.settings.success_delete_wallet.SuccessDeleteWalletActivity;
import io.muun.apollo.presentation.ui.setup_p2p.SetupP2PActivity;
import io.muun.apollo.presentation.ui.setup_password.SetupPasswordActivity;
import io.muun.apollo.presentation.ui.setup_pin_code.SetUpPinCodeActivity;
import io.muun.apollo.presentation.ui.show_qr.ShowQrActivity;
import io.muun.apollo.presentation.ui.signup.SignupActivity;
import io.muun.apollo.presentation.ui.single_action.SingleActionActivity;
import io.muun.apollo.presentation.ui.single_action.V2SingleActionActivity;
import io.muun.apollo.presentation.ui.taproot_setup.TaprootSetupActivity;

import dagger.Subcomponent;


@PerActivity
@Subcomponent
public interface ActivityComponent {

    void inject(HomeActivity activity);

    void inject(SignupActivity activity);

    void inject(LauncherActivity launcherActivity);

    void inject(FeedbackActivity feedbackActivity);

    void inject(OperationDetailActivity operationDetailActivity);

    void inject(DebugPanelActivity debugPanelActivity);

    void inject(ShowQrActivity showQrActivity);

    void inject(ScanQrActivity scanQrActivity);

    void inject(SingleActionActivity singleActionActivity);

    void inject(NewOperationActivity newOperationActivity);

    void inject(EditUsernameActivity settingsActivity);

    void inject(SelectCurrencyActivity selectPrimaryCurrencyActivity);

    void inject(SetUpPinCodeActivity setUpPinCodeActivity);

    void inject(SecurityLogoutActivity securityLogoutActivity);

    void inject(SetupRecoveryCodeActivity activity);

    void inject(EditPasswordActivity editPasswordActivity);

    void inject(EmergencyKitActivity emergencyKitActivity);

    void inject(TaprootSetupActivity taprootSetupActivity);

    void inject(SelectCountryActivity activity);

    void inject(V2SingleActionActivity v2SingleActionActivity);

    void inject(SetupP2PActivity setupP2PActivity);

    void inject(RecoveryToolActivity recoveryToolActivity);

    void inject(SelectBitcoinUnitActivity activity);

    void inject(SendActivity sendActivity);

    void inject(SuccessDeleteWalletActivity successDeleteWalletActivity);

    void inject(SetupPasswordActivity setupPasswordActivity);

    void inject(AnonFeedbackActivity anonFeedbackActivity);

    void inject(MigrationActivity migrationActivity);

    void inject(OperationsActivity operationsActivity);

    void inject(SingleFragmentActivityImpl singleFragmentActivityImpl);

    void inject(SelectAmountActivity selectAmountActivity);

    void inject(SelectNightModeActivity selectDarkModeActivity);

    void inject(LnUrlIntroActivity lnUrlIntroActivity);

    void inject(LnUrlWithdrawConfirmActivity lnUrlWithdrawConfirmActivity);

    void inject(LnUrlWithdrawActivity lnUrlWithdrawActivity);

    void inject(HighFeesExplanationActivity highFeesExplanationActivity);
}
