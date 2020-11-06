package io.muun.apollo.domain.action.di;

import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.CurrencyActions;
import io.muun.apollo.domain.action.IntegrityActions;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.SigninActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.challenge_keys.CreateChallengeSetupAction;
import io.muun.apollo.domain.action.challenge_keys.SetUpChallengeKeyAction;
import io.muun.apollo.domain.action.challenge_keys.StoreChallengeKeyAction;
import io.muun.apollo.domain.action.challenge_keys.password_change.FinishPasswordChangeAction;
import io.muun.apollo.domain.action.challenge_keys.password_change.StartPasswordChangeAction;
import io.muun.apollo.domain.action.challenge_keys.password_setup.SetUpPasswordAction;
import io.muun.apollo.domain.action.challenge_keys.password_setup.StartEmailSetupAction;
import io.muun.apollo.domain.action.challenge_keys.recovery_code_setup.SetUpRecoveryCodeAction;
import io.muun.apollo.domain.action.debug.ForceCrashReportAction;
import io.muun.apollo.domain.action.ek.RenderEmergencyKitAction;
import io.muun.apollo.domain.action.ek.ReportEmergencyKitExportedAction;
import io.muun.apollo.domain.action.ek.VerifyEmergencyKitAction;
import io.muun.apollo.domain.action.fcm.ForceFetchFcmAction;
import io.muun.apollo.domain.action.fcm.GetFcmTokenAction;
import io.muun.apollo.domain.action.fcm.UpdateFcmTokenAction;
import io.muun.apollo.domain.action.keys.DecryptAndStoreKeySetAction;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.FetchNextTransactionSizeAction;
import io.muun.apollo.domain.action.operation.ResolveBitcoinUriAction;
import io.muun.apollo.domain.action.operation.ResolveMuunUriAction;
import io.muun.apollo.domain.action.operation.ResolveOperationUriAction;
import io.muun.apollo.domain.action.operation.SubmitPaymentAction;
import io.muun.apollo.domain.action.operation.UpdateOperationAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.action.session.CreateFirstSessionAction;
import io.muun.apollo.domain.action.session.CreateLoginSessionAction;
import io.muun.apollo.domain.action.session.LogInAction;
import io.muun.apollo.domain.action.session.SyncApplicationDataAction;
import io.muun.apollo.domain.action.session.UseMuunLinkAction;
import io.muun.apollo.domain.action.session.rc_only.FinishLoginWithRcAction;
import io.muun.apollo.domain.action.session.rc_only.LogInWithRcAction;
import io.muun.apollo.domain.action.user.EmailLinkAction;
import io.muun.apollo.domain.action.user.SendEncryptedKeysEmailAction;
import io.muun.apollo.domain.action.user.UpdateProfilePictureAction;

public interface ActionComponent {

    // Action bags:

    SigninActions signinActions();

    ContactActions contactActions();

    OperationActions operationActions();

    UserActions userActions();

    CurrencyActions currencyActions();

    NotificationActions notificationActions();

    AsyncActionStore asyncActionStore();

    IntegrityActions integrityActions();

    // Own-class actions:
    UpdateProfilePictureAction updateProfilePictureAction();

    FetchRealTimeDataAction fetchRealTimeDataAction();

    FetchNextTransactionSizeAction fetchNextTransactionSizeAction();

    ResolveBitcoinUriAction resolveBitcoinUriAction();

    ResolveMuunUriAction resolveMuunUriAction();

    ResolveOperationUriAction resolveOperationUriAction();

    SubmitPaymentAction submitOutgoingPaymentAction();

    CreateOperationAction createOperationAction();

    UpdateOperationAction updateOperationAction();

    SendEncryptedKeysEmailAction sendEncryptedKeysEmailAction();

    LogInAction logInAction();

    DecryptAndStoreKeySetAction decryptAndStoreKeySetAction();

    SyncApplicationDataAction syncApplicationDataAction();

    UpdateFcmTokenAction updateFcmTokenAction();

    GetFcmTokenAction getFcmTokenAction();

    ForceFetchFcmAction forceFetchFcmTokenAction();

    StoreChallengeKeyAction storeChallengeKeyAction();

    CreateChallengeSetupAction createChallengeSetupAction();

    SetUpChallengeKeyAction setUpChallengeKeyAction();

    SetUpRecoveryCodeAction setUpRecoveryCodeAction();

    CreateFirstSessionAction createFirstSessionAction();

    LogInWithRcAction logInWithRcAction();

    FinishLoginWithRcAction finishLoginWithRcAction();

    StartEmailSetupAction startEmailSetupAction();

    SetUpPasswordAction setupPasswordAction();

    StartPasswordChangeAction startPasswordChangeAction();

    FinishPasswordChangeAction finishPasswordChangeAction();

    CreateLoginSessionAction createLoginSessionAction();

    ReportEmergencyKitExportedAction reportKeysExportedAction();

    ForceCrashReportAction forceCrashReportAction();

    RenderEmergencyKitAction renderEmergencyKitAction();

    VerifyEmergencyKitAction verifyEmergencyKitAction();

    UseMuunLinkAction useMuunLinkAction();

    EmailLinkAction emailLinkAction();
}
