package io.muun.apollo.domain.action.di;

import io.muun.apollo.domain.action.AddressActions;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.CurrencyActions;
import io.muun.apollo.domain.action.HardwareWalletActions;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.PhoneActions;
import io.muun.apollo.domain.action.SatelliteActions;
import io.muun.apollo.domain.action.SigninActions;
import io.muun.apollo.domain.action.SyncActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.FetchNextTransactionSizeAction;
import io.muun.apollo.domain.action.operation.ResolveBitcoinUriAction;
import io.muun.apollo.domain.action.operation.ResolveMuunUriAction;
import io.muun.apollo.domain.action.operation.ResolveOperationUriAction;
import io.muun.apollo.domain.action.operation.SubmitIncomingPaymentAction;
import io.muun.apollo.domain.action.operation.SubmitOutgoingPaymentAction;
import io.muun.apollo.domain.action.operation.SubmitPaymentAction;
import io.muun.apollo.domain.action.operation.UpdateOperationAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.action.user.SendEncryptedKeysEmailAction;
import io.muun.apollo.domain.action.user.UpdateProfilePictureAction;

public interface ActionComponent {

    // Action bags:
    PhoneActions phoneActions();

    SigninActions signinActions();

    ContactActions contactActions();

    OperationActions operationActions();

    UserActions userActions();

    CurrencyActions currencyActions();

    SyncActions feeActions();

    AddressActions addressActions();

    NotificationActions notificationActions();

    AsyncActionStore asyncActionStore();

    SatelliteActions satelliteActions();

    HardwareWalletActions hardwareWalletActions();

    // Own-class actions:
    UpdateProfilePictureAction updateProfilePictureAction();

    FetchRealTimeDataAction fetchRealTimeDataAction();

    FetchNextTransactionSizeAction fetchNextTransactionSizeAction();

    ResolveBitcoinUriAction resolveBitcoinUriAction();

    ResolveMuunUriAction resolveMuunUriAction();

    ResolveOperationUriAction resolveOperationUriAction();

    SubmitPaymentAction submitPaymentAction();

    SubmitIncomingPaymentAction submitIncomingPaymentAction();

    SubmitOutgoingPaymentAction submitOutgoingPaymentAction();

    CreateOperationAction createOperationAction();

    UpdateOperationAction updateOperationAction();

    SendEncryptedKeysEmailAction sendEncryptedKeysEmailAction();
}
