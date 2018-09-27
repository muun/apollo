package io.muun.apollo.domain.action.di;

import io.muun.apollo.domain.action.AddressActions;
import io.muun.apollo.domain.action.BitcoinActions;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.CurrencyActions;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.PhoneActions;
import io.muun.apollo.domain.action.SigninActions;
import io.muun.apollo.domain.action.SyncActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.base.AsyncActionStore;

public interface ActionComponent {

    // Exposed to sub-components

    PhoneActions phoneActions();

    SigninActions signinActions();

    ContactActions contactActions();

    BitcoinActions transactionActions();

    OperationActions operationActions();

    UserActions userActions();

    CurrencyActions currencyActions();

    SyncActions feeActions();

    AddressActions addressActions();

    NotificationActions notificationActions();

    AsyncActionStore asyncActionStore();
}
