package io.muun.apollo.presentation.ui.base.di;

import io.muun.apollo.presentation.ui.fragments.create_email_help.CreateEmailHelpFragment;
import io.muun.apollo.presentation.ui.fragments.create_password.CreatePasswordFragment;
import io.muun.apollo.presentation.ui.fragments.ek_intro.EmergencyKitIntroFragment;
import io.muun.apollo.presentation.ui.fragments.ek_save.EmergencyKitSaveFragment;
import io.muun.apollo.presentation.ui.fragments.ek_success.EmergencyKitSuccessFragment;
import io.muun.apollo.presentation.ui.fragments.ek_verify.EmergencyKitVerifyFragment;
import io.muun.apollo.presentation.ui.fragments.ek_verify_cloud.EmergencyKitCloudVerifyFragment;
import io.muun.apollo.presentation.ui.fragments.ek_verify_help.EmergencyKitVerifyHelpFragment;
import io.muun.apollo.presentation.ui.fragments.enter_email.CreateEmailFragment;
import io.muun.apollo.presentation.ui.fragments.enter_password.EnterPasswordFragment;
import io.muun.apollo.presentation.ui.fragments.enter_recovery_code.EnterRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.fragments.error.ErrorFragment;
import io.muun.apollo.presentation.ui.fragments.explanation_block.ExplanationPageFragment;
import io.muun.apollo.presentation.ui.fragments.home.HomeFragment;
import io.muun.apollo.presentation.ui.fragments.landing.LandingFragment;
import io.muun.apollo.presentation.ui.fragments.loading.LoadingFragment;
import io.muun.apollo.presentation.ui.fragments.login_authorize.LoginAuthorizeFragment;
import io.muun.apollo.presentation.ui.fragments.login_email.LoginEmailFragment;
import io.muun.apollo.presentation.ui.fragments.manual_fee.ManualFeeFragment;
import io.muun.apollo.presentation.ui.fragments.need_recovery_code.NeedRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorFragment;
import io.muun.apollo.presentation.ui.fragments.operations.OperationsFragment;
import io.muun.apollo.presentation.ui.fragments.password_setup_intro.SetupPasswordIntroFragment;
import io.muun.apollo.presentation.ui.fragments.phone_number.PhoneNumberFragment;
import io.muun.apollo.presentation.ui.fragments.profile.ProfileFragment;
import io.muun.apollo.presentation.ui.fragments.rc_only_login.RcOnlyLoginFragment;
import io.muun.apollo.presentation.ui.fragments.rc_only_login_auth.RcLoginEmailAuthorizeFragment;
import io.muun.apollo.presentation.ui.fragments.recommended_fee.RecommendedFeeFragment;
import io.muun.apollo.presentation.ui.fragments.recovery_tool.RecoveryToolFragment;
import io.muun.apollo.presentation.ui.fragments.security_center.SecurityCenterFragment;
import io.muun.apollo.presentation.ui.fragments.settings.SettingsFragment;
import io.muun.apollo.presentation.ui.fragments.setup_password_accept.SetupPasswordAcceptFragment;
import io.muun.apollo.presentation.ui.fragments.setup_password_success.SetupPasswordSuccessFragment;
import io.muun.apollo.presentation.ui.fragments.sync.SyncFragment;
import io.muun.apollo.presentation.ui.fragments.sync_contacts.SyncContactsFragment;
import io.muun.apollo.presentation.ui.fragments.tr_clock_detail.TaprootClockDetailFragment;
import io.muun.apollo.presentation.ui.fragments.tr_intro.TaprootIntroFragment;
import io.muun.apollo.presentation.ui.fragments.tr_success.TaprootSuccessFragment;
import io.muun.apollo.presentation.ui.fragments.verification_code.VerificationCodeFragment;
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailFragment;
import io.muun.apollo.presentation.ui.recovery_code.accept.AcceptRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.recovery_code.priming.PrimingRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.recovery_code.show.ShowRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.recovery_code.success.SuccessRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.recovery_code.verify.VerifyRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.settings.EmailWaitFragment;
import io.muun.apollo.presentation.ui.settings.OldPasswordFragment;
import io.muun.apollo.presentation.ui.settings.RecoveryCodeFragment;
import io.muun.apollo.presentation.ui.settings.bitcoin.BitcoinSettingsFragment;
import io.muun.apollo.presentation.ui.settings.edit_password.ChangePasswordFragment;
import io.muun.apollo.presentation.ui.settings.edit_password.StartPasswordChangeFragment;
import io.muun.apollo.presentation.ui.settings.edit_password.success.EditPasswordSuccessFragment;
import io.muun.apollo.presentation.ui.settings.lightning.LightningSettingsFragment;
import io.muun.apollo.presentation.ui.show_qr.bitcoin.BitcoinAddressQrFragment;
import io.muun.apollo.presentation.ui.show_qr.ln.LnInvoiceQrFragment;

import dagger.Subcomponent;

@PerFragment
@Subcomponent
public interface FragmentComponent {

    void inject(PhoneNumberFragment fragment);

    void inject(ProfileFragment fragment);

    void inject(VerificationCodeFragment verificationCodeFragment);

    void inject(OperationsFragment operationsFragment);

    void inject(CreatePasswordFragment createPasswordFragment);

    void inject(SyncFragment syncFragment);

    void inject(EnterPasswordFragment enterPasswordFragment);

    void inject(LandingFragment landingFragment);

    void inject(LoginAuthorizeFragment loginAuthorizeFragment);

    void inject(ShowRecoveryCodeFragment fragment);

    void inject(VerifyRecoveryCodeFragment fragment);

    void inject(AcceptRecoveryCodeFragment fragment);

    void inject(EnterRecoveryCodeFragment enterRecoveryCodeFragment);

    void inject(SuccessRecoveryCodeFragment successFragment);

    void inject(EditPasswordSuccessFragment successFragment);

    void inject(StartPasswordChangeFragment startPasswordChangeFragment);

    void inject(OldPasswordFragment oldPasswordFragment);

    void inject(EmailWaitFragment emailWaitFragment);

    void inject(ChangePasswordFragment changePasswordFragment);

    void inject(RecoveryCodeFragment recoveryCodeFragment);

    void inject(NeedRecoveryCodeFragment fragment);

    void inject(CreateEmailFragment fragment);

    void inject(LoginEmailFragment fragment);

    void inject(SyncContactsFragment syncContactsFragment);

    void inject(NewOperationErrorFragment newOperationErrorFragment);

    void inject(RecommendedFeeFragment recommendedFeeFragment);

    void inject(ManualFeeFragment manualFeeFragment);

    void inject(LoadingFragment loadingFragment);

    void inject(ErrorFragment errorFragment);

    void inject(EmergencyKitSuccessFragment emergencyKitSuccessFragment);

    void inject(TaprootSuccessFragment taprootSuccessFragment);

    void inject(TaprootClockDetailFragment taprootClockDetailFragment);

    void inject(RecoveryToolFragment recoveryToolFragment);

    void inject(ExplanationPageFragment explanationFragment);

    void inject(EmergencyKitIntroFragment emergencyKitIntroFragment);

    void inject(CreateEmailHelpFragment createEmailHelpFragment);

    void inject(BitcoinAddressQrFragment segwitAddressQrFragment);

    void inject(LnInvoiceQrFragment legacyAddressQrFragment);

    void inject(SecurityCenterFragment securityCenterFragment);

    void inject(PrimingRecoveryCodeFragment successFragment);

    void inject(SetupPasswordIntroFragment setupPasswordIntroFragment);

    void inject(SetupPasswordSuccessFragment setupPasswordIntroFragment);

    void inject(VerifyEmailFragment verifyEmailFragment);

    void inject(SetupPasswordAcceptFragment setupPasswordAcceptFragment);

    void inject(EmergencyKitSaveFragment emergencyKitSaveFragment);

    void inject(EmergencyKitVerifyFragment emergencyKitVerifyFragment);

    void inject(EmergencyKitVerifyHelpFragment emergencyKitVerifyHelpFragment);

    void inject(RcOnlyLoginFragment rcOnlyLoginFragment);

    void inject(RcLoginEmailAuthorizeFragment rcLoginEmailAuthorizeFragment);

    void inject(EmergencyKitCloudVerifyFragment emergencyKitCloudCloudVerifyFragment);

    void inject(SettingsFragment settingsFragment);

    void inject(HomeFragment homeFragment);

    void inject(LightningSettingsFragment lightningSettingsFragment);

    void inject(BitcoinSettingsFragment bitcoinSettingsFragment);

    void inject(TaprootIntroFragment taprootIntroFragment);
}
