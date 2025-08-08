package io.muun.apollo.presentation.app;

import io.muun.apollo.R;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.analytics.NewOperationOrigin;
import io.muun.apollo.domain.model.FeedbackCategory;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.presentation.app.di.PerApplication;
import io.muun.apollo.presentation.ui.activity.operations.OperationsActivity;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivityImpl;
import io.muun.apollo.presentation.ui.debug.DebugPanelActivity;
import io.muun.apollo.presentation.ui.diagnostic.DiagnosticActivity;
import io.muun.apollo.presentation.ui.export_keys.EmergencyKitActivity;
import io.muun.apollo.presentation.ui.feedback.anon.AnonFeedbackActivity;
import io.muun.apollo.presentation.ui.feedback.email.FeedbackActivity;
import io.muun.apollo.presentation.ui.fragments.need_recovery_code.NeedRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.fragments.need_recovery_code.NeedRecoveryCodeFragment.Flow;
import io.muun.apollo.presentation.ui.high_fees.HighFeesExplanationActivity;
import io.muun.apollo.presentation.ui.home.HomeActivity;
import io.muun.apollo.presentation.ui.launcher.LauncherActivity;
import io.muun.apollo.presentation.ui.lnurl.intro.LnUrlIntroActivity;
import io.muun.apollo.presentation.ui.lnurl.withdraw.LnUrlWithdrawActivity;
import io.muun.apollo.presentation.ui.lnurl.withdraw.confirm.LnUrlWithdrawConfirmActivity;
import io.muun.apollo.presentation.ui.migration.MigrationActivity;
import io.muun.apollo.presentation.ui.new_operation.NewOperationActivity;
import io.muun.apollo.presentation.ui.nfc.NfcReaderActivity;
import io.muun.apollo.presentation.ui.operation_detail.OperationDetailActivity;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity;
import io.muun.apollo.presentation.ui.recovery_tool.RecoveryToolActivity;
import io.muun.apollo.presentation.ui.scan_qr.LnUrlFlow;
import io.muun.apollo.presentation.ui.scan_qr.ScanQrActivity;
import io.muun.apollo.presentation.ui.security_logout.SecurityLogoutActivity;
import io.muun.apollo.presentation.ui.select_bitcoin_unit.SelectBitcoinUnitActivity;
import io.muun.apollo.presentation.ui.select_night_mode.SelectNightModeActivity;
import io.muun.apollo.presentation.ui.send.SendActivity;
import io.muun.apollo.presentation.ui.settings.edit_password.EditPasswordActivity;
import io.muun.apollo.presentation.ui.settings.edit_username.EditUsernameActivity;
import io.muun.apollo.presentation.ui.settings.success_delete_wallet.SuccessDeleteWalletActivity;
import io.muun.apollo.presentation.ui.setup_p2p.SetupP2PActivity;
import io.muun.apollo.presentation.ui.setup_password.SetupPasswordActivity;
import io.muun.apollo.presentation.ui.show_qr.ShowQrActivity;
import io.muun.apollo.presentation.ui.signup.SignupActivity;
import io.muun.apollo.presentation.ui.single_action.SingleActionActivity;
import io.muun.apollo.presentation.ui.single_action.V2SingleActionActivity;
import io.muun.apollo.presentation.ui.taproot_setup.TaprootSetupActivity;
import io.muun.apollo.presentation.ui.utils.ExtensionsKt;
import io.muun.apollo.presentation.ui.utils.OS;
import io.muun.common.Optional;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import timber.log.Timber;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;


/**
 * Class used to navigate through the application.
 */
@PerApplication
public class Navigator {

    private static final String PLAY_STORE_PACKAGE_NAME = "io.muun.apollo";

    private final UserSelector userSel;

    @Inject
    public Navigator(UserSelector userSelector) {
        this.userSel = userSelector;
    }

    /**
     * Takes the user to the Signup screen.
     *
     * @param context an {Activity} context.
     */
    public void navigateToSignup(@NotNull Context context) {
        final Intent intent = SignupActivity.getStartActivityIntent(context);
        clearBackStack(intent);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(intent);
    }

    /**
     * Takes a NEW user to the Home screen.
     *
     * @param context an {Activity} context.
     */
    public void navigateNewUserToHome(@NotNull Context context) {
        final Intent intent = HomeActivity.getStartActivityIntentForNewUser(context);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    /**
     * Takes the user to the Home screen.
     *
     * @param context an {Activity} context.
     */
    public void navigateToHome(@NotNull Context context) {
        final Intent intent = HomeActivity.getStartActivityIntent(context);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    /**
     * Takes the user to the Home screen.
     *
     * @param context an {Activity} context.
     */
    public void navigateToHome(@NotNull Context context, final Operation operation) {
        final Intent intent = HomeActivity.getStartActivityIntent(context, operation);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    /**
     * Takes the user to the RequestUpdate screen.
     *
     * @param context an {Activity} context.
     */
    public void navigateToRequestUpdate(@NotNull Context context) {
        navigateToSingleAction(context, SingleActionActivity.ActionType.REQUEST_UPDATE, true);
    }

    /**
     * Takes the user to the RequestRestart screen.
     *
     * @param context an {Activity} context.
     */
    public void navigateToRequestRestart(@NotNull Context context) {
        navigateToSingleAction(context, SingleActionActivity.ActionType.REQUEST_RESTART, true);
    }

    private void navigateToSingleAction(@NotNull Context context,
                                        @NotNull SingleActionActivity.ActionType actionType,
                                        boolean clearBackStack) {

        final Intent intent = SingleActionActivity.getIntent(context, actionType);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        if (clearBackStack) {
            clearBackStack(intent);
        }

        context.startActivity(intent);
    }

    /**
     * Takes the user to the debug panel.
     *
     * @param context an {Activity} context.
     */
    public void navigateToDebugPanel(@NotNull Context context) {

        if (!Globals.INSTANCE.isDebug() || Globals.INSTANCE.isProduction()) {
            return; // Line of Last Defense, avoid showing DebugPanel when not intended
        }

        final Intent intent = DebugPanelActivity.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the diagnostic screen.
     */
    public void navigateToDiagnosticMode(Context context) {
        final Intent intent = DiagnosticActivity.Companion.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the Send screen.
     */
    public void navigateToSend(@NotNull Context context) {
        final Intent intent = SendActivity.Companion.getStartActivityIntent(context);
        context.startActivity(intent);
    }

    /**
     * Takes the user to the New Operation screen, to send a payment to a contact.
     *
     * @param context an {Activity} context.
     * @param uri     an {OperationUri} instance.
     */
    public void navigateToNewOperation(@NotNull Context context,
                                       @NotNull NewOperationOrigin origin,
                                       @NotNull OperationUri uri) {

        final Intent intent = NewOperationActivity.Companion.getIntent(context, origin, uri);
        context.startActivity(intent);
    }

    /**
     * Takes the user to the Operation detail screen.
     *
     * @param context     an {Activity} context.
     * @param operationId the operation id.
     */
    public void navigateToOperationDetail(@NotNull Context context, @NotNull Long operationId) {
        final Intent intent = OperationDetailActivity.getStartActivityIntent(context, operationId);
        context.startActivity(intent);
    }

    /**
     * Open app chooser to share text with an external application.
     *
     * @param context an {Activity} context.
     * @param text    text to share.
     * @param title   "Share with:" text.
     */
    public void shareText(@NotNull Context context, String text, String title) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);

        // Always display the chooser
        context.startActivity(Intent.createChooser(intent, title));
    }

    /**
     * Open app chooser to send an email to Muun support.
     */
    public void sendSupportEmail(@NotNull Context context) {
        final Intent emailIntent = Email.INSTANCE.composeSupportEmail();
        context.startActivity(Intent.createChooser(emailIntent, "Muun"));
    }

    /**
     * Makes the intent clear the back stack.
     */
    private void clearBackStack(@NotNull Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * Takes the user to the Show Qr screen.
     */
    public void navigateToShowQr(@NotNull Context context,
                                 AnalyticsEvent.RECEIVE_ORIGIN origin) {

        final Intent intent = ShowQrActivity.getStartActivityIntent(context, origin);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to LNURL Withdraw intro screen.
     */
    public void navigateToLnUrlIntro(@NotNull Context context) {
        final Intent intent = LnUrlIntroActivity.Companion.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the Scan Qr screen.
     */
    public void navigateToScanQr(@NotNull Context context) {
        final Intent intent = ScanQrActivity.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the Scan Qr screen, as part of an LNURL flow.
     */
    public void navigateToLnUrlWithdrawScanQr(@NotNull Context context, @NotNull LnUrlFlow flow) {
        final Intent intent = ScanQrActivity.getStartActivityIntentForLnurl(context, flow);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the LNURL Withdraw screen.
     */
    public void navigateToLnUrlWithdraw(@NotNull Context ctx, @NotNull String lnurl) {
        final Intent intent = LnUrlWithdrawActivity.Companion.getStartActivityIntent(ctx, lnurl);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        ctx.startActivity(intent);
    }

    /**
     * Takes the user to the confirm LNURL Withdraw screen.
     */
    public void navigateToLnUrlWithdrawConfirm(@NotNull Context context, @NotNull String lnurl) {
        final Intent intent = LnUrlWithdrawConfirmActivity.Companion
                .getStartActivityIntent(context, lnurl);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the Feedback screen, getting the SupportID from UserSelector.
     */
    public void navigateToSendGenericFeedback(@NotNull Context context) {
        navigateToSendGenericFeedback(context, userSel.getOptional().flatMap(User::getSupportId));
    }

    /**
     * Takes the user to the Feedback screen, using the provided SupportID.
     */
    public void navigateToSendGenericFeedback(@NotNull Context context,
                                              Optional<String> maybeSupportId) {

        navigateToSendFeedback(
                context,
                FeedbackCategory.GENERAL,
                context.getString(R.string.feedback_explanation),
                maybeSupportId
        );
    }

    /**
     * Takes the user to the Feedback screen.
     */
    public void navigateToSendErrorFeedback(@NotNull Context context) {
        final Optional<String> maybeSupportId = userSel.getOptional().flatMap(User::getSupportId);

        navigateToSendFeedback(
                context,
                FeedbackCategory.ERROR,
                context.getString(R.string.feedback_explanation),
                maybeSupportId
        );
    }

    private void navigateToSendFeedback(@NotNull Context ctx,
                                        @NotNull FeedbackCategory category,
                                        @NotNull String explanation,
                                        Optional<String> maybeSupportId) {

        // Go to the anonymous feedback screen if not logged in or email and password not set up:
        final boolean goToAnonFeedback = userSel.getOptional()
                .map(it -> !it.hasPassword)
                .orElse(true);

        final Intent intent;

        if (goToAnonFeedback) {
            intent = AnonFeedbackActivity.getStartActivityIntent(ctx, maybeSupportId);
        } else {
            intent = FeedbackActivity.getStartActivityIntent(ctx, category, explanation);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        ctx.startActivity(intent);
    }

    /**
     * Takes the user to the settings screen.
     */
    public void navigateToRecoveryTool(Context context) {
        final Intent intent = RecoveryToolActivity.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the bitcoin unit selection screen.
     */
    public void navigateToSelectBitcoinUnit(Context context) {
        final Intent intent = SelectBitcoinUnitActivity.Companion.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the Dark Mode selection screen.
     */
    public void navigateToSelectDarkMode(Context context) {
        final Intent intent = SelectNightModeActivity.Companion.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the edit username screen.
     */
    public void navigateToEditUsername(Context context) {
        final Intent intent = EditUsernameActivity.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the change password flow.
     */
    public void navigateToEditPassword(Context context) {
        final Intent intent = EditPasswordActivity.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the export keys introduction screen, start of the Export Keys flow.
     */
    public void navigateToExportKeysIntro(Context context) {
        final Intent intent = EmergencyKitActivity.Companion.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the export keys introduction screen, start of the Export Keys flow.
     */
    public void navigateToTaprootSetup(Context context) {
        final Intent intent = TaprootSetupActivity.Companion.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Restart the application.
     */
    public void navigateToLauncher(@NotNull Context context) {
        Timber.i("Navigating to LauncherActivity");
        final Intent intent = new Intent(context, LauncherActivity.class);
        clearBackStack(intent);

        context.startActivity(intent);
    }

    /**
     * Navigates to the delete wallet success screen.
     * Meant to be used after successfully deleting a wallet.
     * CAREFUL! Code after this call can fail when it finds empty repositories/databases.
     */
    public void navigateToDeleteWallet(@NotNull Context context, Optional<String> maybeSupportId) {
        final Intent intent = SuccessDeleteWalletActivity
                .getStartActivityIntent(context, maybeSupportId);

        clearBackStack(intent);
        context.startActivity(intent);
    }

    /**
     * Takes the user to the security logout screen.
     */
    public void navigateToSecurityLogout(@NotNull Context context) {
        final Intent intent = SecurityLogoutActivity.getStartActivityIntent(context);

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        clearBackStack(intent);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the recovery code screen.
     */
    public void navigateToRecoveryCode(@NotNull Context context) {
        final Intent intent = SetupRecoveryCodeActivity
                .getStartActivityIntent(context)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the missing recovery code screen.
     */
    public void navigateToMissingRecoveryCode(@NotNull Context context, Flow flow) {
        final Bundle arguments = new Bundle();
        arguments.putString(NeedRecoveryCodeFragment.FLOW, flow.name());

        final var actionType = V2SingleActionActivity.ActionType.REQUEST_RECOVERY_CODE_SETUP;
        final Intent intent = V2SingleActionActivity
                .getIntent(context, actionType, arguments)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Takes the user to Setup P2P flow.
     */
    public void navigateToSetupP2P(@NotNull Context context) {
        final Intent intent = SetupP2PActivity.getStartActivityIntent(context);
        context.startActivity(intent);
    }

    /**
     * Takes the user to Muun page in the PlayStore, in a new Task.
     */
    public void openPlayStore(Context context) {
        final String pkgName = PLAY_STORE_PACKAGE_NAME; // hard-coded to work in debug mode
        final String playStoreAppUri = "market://details?id=" + pkgName;
        final String playStoreWebUri = "https://play.google.com/store/apps/details?id=" + pkgName;

        try {
            ExtensionsKt.openUri(context, playStoreAppUri);
        } catch (ActivityNotFoundException ex) {
            // Play Store not available. Strange, but we can still fire a browser:
            ExtensionsKt.openInBrowser(context, playStoreWebUri);
        }
    }

    /**
     * Takes the user to their email client.
     */
    public void navigateToEmailClient(@NotNull Context context) {

        final Intent intent = Email.INSTANCE.getEmailClientIntent()
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Timber.e("Email client not found!");
            final String message = context.getString(R.string.error_no_email_client_installed);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Takes the user to system's Muun settings menu. Useful for re-enabling permissions that were
     * permanently denied.
     */
    public void navigateToSystemSettings(Context context) {
        final Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        context.startActivity(intent);
    }

    /**
     * Takes the user to the password setup screen.
     */
    public void navigateToSetupPassword(@NotNull Context context) {
        final Intent intent = SetupPasswordActivity.Companion.getStartActivityIntent(context);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(intent);
    }

    /**
     * Takes the user to the API migrations screen.
     */
    public void navigateToMigrations(@NotNull final Context context) {
        final Intent intent = MigrationActivity.Companion.getStartActivityIntent(context);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        context.startActivity(intent);
    }

    /**
     * Takes the user to the Operations (aka Payment History) screen.
     * Note that, unlike the MANY other methods of this class, this method requires an Activity,
     * instead of a Context, this is because in this case we apply a transition animation.
     */
    public void navigateToOperations(@NotNull Activity activity) {
        final Intent intent = OperationsActivity.Companion.getStartActivityIntent(activity);

        if (OS.supportsActivityTransitions()) {
            final ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity);
            activity.startActivity(intent, options.toBundle());

        } else {
            activity.startActivity(intent);
        }
    }

    /**
     * Takes the user to the specified Fragment.
     */
    public void navigateToFragment(final Context context,
                                   final Class<? extends SingleFragment<?>> fragment) {
        context.startActivity(
                SingleFragmentActivityImpl.Companion.getStartActivityIntent(context, fragment)
        );
    }

    /**
     * Takes the user to the High Fees explanation activity.
     */
    public void navigateToHighFeesExplanation(@NotNull final Context context) {
        final Intent intent = HighFeesExplanationActivity.Companion.getStartActivityIntent(context);

        // No animation between activities for now
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        context.startActivity(intent);
    }

    /**
     * Navigates to NFC Reader activity ready to read a tag.
     *
     * @param context the caller context from which animate this.
     * @param activityLauncher the launcher for result that's going to
     *                         retrieve the status
     */
    public void navigateToNfcReaderActivityForResult(
            @NotNull Context context,
            ActivityResultLauncher<Intent> activityLauncher
    ) {
        activityLauncher.launch(
                NfcReaderActivity.Companion.getStartActivityIntent(context)
        );
    }
}
