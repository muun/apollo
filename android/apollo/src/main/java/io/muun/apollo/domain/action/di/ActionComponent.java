package io.muun.apollo.domain.action.di;

import io.muun.apollo.domain.LoggingContextManager;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.CurrencyActions;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.NotificationPoller;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.SigninActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.challenge_keys.password_change.FinishPasswordChangeAction;
import io.muun.apollo.domain.action.challenge_keys.password_change.StartPasswordChangeAction;
import io.muun.apollo.domain.action.challenge_keys.password_setup.SetUpPasswordAction;
import io.muun.apollo.domain.action.challenge_keys.password_setup.StartEmailSetupAction;
import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.StartRecoveryCodeSetupAction;
import io.muun.apollo.domain.action.ek.AddEmergencyKitMetadataAction;
import io.muun.apollo.domain.action.ek.RenderEmergencyKitAction;
import io.muun.apollo.domain.action.ek.ReportEmergencyKitExportedAction;
import io.muun.apollo.domain.action.ek.UploadToDriveAction;
import io.muun.apollo.domain.action.ek.VerifyEmergencyKitAction;
import io.muun.apollo.domain.action.fcm.ForceFetchFcmAction;
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction;
import io.muun.apollo.domain.action.incoming_swap.GenerateInvoiceAction;
import io.muun.apollo.domain.action.incoming_swap.RegisterInvoicesAction;
import io.muun.apollo.domain.action.integrity.IntegrityAction;
import io.muun.apollo.domain.action.operation.ResolveLnInvoiceAction;
import io.muun.apollo.domain.action.operation.ResolveOperationUriAction;
import io.muun.apollo.domain.action.operation.SubmitPaymentAction;
import io.muun.apollo.domain.action.permission.UpdateContactsPermissionStateAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeFeesAction;
import io.muun.apollo.domain.action.session.CreateLoginSessionAction;
import io.muun.apollo.domain.action.session.LogInAction;
import io.muun.apollo.domain.action.session.SyncApplicationDataAction;
import io.muun.apollo.domain.action.session.UseMuunLinkAction;
import io.muun.apollo.domain.action.session.rc_only.LogInWithRcAction;
import io.muun.apollo.domain.action.user.DeleteWalletAction;
import io.muun.apollo.domain.action.user.EmailLinkAction;
import io.muun.apollo.domain.action.user.UpdateProfilePictureAction;
import io.muun.apollo.domain.debug.DebugExecutable;

import dagger.Component;

/**
 * Dagger Component. {@link Component}.
 * Add here:
 * - members-injection methods (e.g for classes which lifecycles are 3rd-party controlled, like
 * Android's). Example: void inject(GcmMessageListenerService service).
 * - provision methods, to expose injected or provided dependencies to other (dependent) components.
 * Example: UpdateProfilePictureAction updateProfilePictureAction();
 */
@SuppressWarnings("checkstyle:MissingJavadocMethod")
public interface ActionComponent {

    // Exposed to dependent components

    // Action bags:

    SigninActions signinActions();

    LoggingContextManager loggingContextManager();

    ContactActions contactActions();

    OperationActions operationActions();

    UserActions userActions();

    CurrencyActions currencyActions();

    NotificationActions notificationActions();

    AsyncActionStore asyncActionStore();

    IntegrityAction integrityAction();

    // Own-class actions:

    NotificationPoller notificationPoller();

    UpdateProfilePictureAction updateProfilePictureAction();

    FetchRealTimeDataAction fetchRealTimeDataAction();

    FetchRealTimeFeesAction fetchRealTimeFeesAction();

    ResolveOperationUriAction resolveOperationUriAction();

    ResolveLnInvoiceAction resolveLnInvoiceAction();

    SubmitPaymentAction submitOutgoingPaymentAction();

    LogInAction logInAction();

    SyncApplicationDataAction syncApplicationDataAction();

    GetFcmTokenAction getFcmTokenAction();

    ForceFetchFcmAction forceFetchFcmTokenAction();

    LogInWithRcAction logInWithRcAction();

    StartEmailSetupAction startEmailSetupAction();

    SetUpPasswordAction setupPasswordAction();

    StartPasswordChangeAction startPasswordChangeAction();

    FinishPasswordChangeAction finishPasswordChangeAction();

    CreateLoginSessionAction createLoginSessionAction();

    ReportEmergencyKitExportedAction reportKeysExportedAction();

    RenderEmergencyKitAction renderEmergencyKitAction();

    VerifyEmergencyKitAction verifyEmergencyKitAction();

    UseMuunLinkAction useMuunLinkAction();

    EmailLinkAction emailLinkAction();

    UploadToDriveAction uploadEmergencyKitAction();

    RegisterInvoicesAction registerInvoicesAction();

    GenerateInvoiceAction generateInvoiceAction();

    AddEmergencyKitMetadataAction addEmergencyKitMetadata();

    StartRecoveryCodeSetupAction startRecoveryCodeSetupAction();

    UpdateContactsPermissionStateAction updateContactsPermissionStateAction();

    DeleteWalletAction deleteWalletAction();

    DebugExecutable debugExecutable();
}
