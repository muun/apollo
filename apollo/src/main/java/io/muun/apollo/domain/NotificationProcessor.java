package io.muun.apollo.domain;


import io.muun.apollo.data.net.ModelObjectsMapper;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.SigninActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.errors.UnknownNotificationTypeError;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.common.Optional;
import io.muun.common.api.NotificationJson;
import io.muun.common.api.messages.AuthorizeChallengeUpdateMessage;
import io.muun.common.api.messages.AuthorizeSigninMessage;
import io.muun.common.api.messages.ContactUpdateMessage;
import io.muun.common.api.messages.EmailVerifiedMessage;
import io.muun.common.api.messages.Message;
import io.muun.common.api.messages.NewContactMessage;
import io.muun.common.api.messages.NewOperationMessage;
import io.muun.common.api.messages.OperationUpdateMessage;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.model.SessionStatus;

import android.support.annotation.VisibleForTesting;
import rx.Completable;
import rx.functions.Func1;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

public class NotificationProcessor {

    private final OperationActions operationActions;
    private final ContactActions contactActions;
    private final UserActions userActions;
    private final SigninActions signinActions;

    private final ModelObjectsMapper mapper;

    private final Map<String, NotificationHandler> handlers =
            new HashMap<>();

    /**
     * Constructor.
     */
    @Inject
    public NotificationProcessor(OperationActions operationActions,
                                 ContactActions contactActions,
                                 UserActions userActions,
                                 SigninActions signinActions,
                                 ModelObjectsMapper mapper) {

        this.operationActions = operationActions;
        this.contactActions = contactActions;
        this.userActions = userActions;
        this.signinActions = signinActions;
        this.mapper = mapper;

        addHandler(
                NewContactMessage.TYPE,
                NewContactMessage.PERMISSION,
                this::handleNewContact);
        addHandler(
                ContactUpdateMessage.TYPE,
                ContactUpdateMessage.PERMISSION,
                this::handleContactUpdate);
        addHandler(
                NewOperationMessage.TYPE,
                NewOperationMessage.PERMISSION,
                this::handleNewOperation);
        addHandler(
                OperationUpdateMessage.TYPE,
                OperationUpdateMessage.PERMISSION,
                this::handleOperationUpdate);
        addHandler(
                EmailVerifiedMessage.TYPE,
                EmailVerifiedMessage.PERMISSION,
                this::handleEmailVerified);
        addHandler(
                AuthorizeSigninMessage.TYPE,
                AuthorizeSigninMessage.PERMISSION,
                this::handleAuthorizedSignin);
        addHandler(
                AuthorizeChallengeUpdateMessage.TYPE,
                AuthorizeChallengeUpdateMessage.PERMISSION,
                this::handleAuthorizedChallengeUpdate);
    }

    /**
     * Process a notification, invoking the relevant handler.
     */
    public Completable process(NotificationJson notification) {
        final NotificationHandler notificationHandler = handlers.get(notification.messageType);

        if (notificationHandler == null) {
            // This Should Not Happen (tm). Houston should not send us notifications that
            // our CLIENT_VERSION does not support.
            throw new UnknownNotificationTypeError(notification.messageType);
        }

        final Optional<SessionStatus> sessionStatus = signinActions.getSessionStatus();
        if (!sessionStatus.isPresent()
                || !sessionStatus.get().hasPermisionFor(notificationHandler.permission)) {
            //The user shouldn't have received this type of notification as it lacks the permissions
            //to view it.
            return Completable.complete();
        }

        return notificationHandler.handler.call(notification);
    }

    private Completable handleNewContact(NotificationJson notification) {
        final NewContactMessage message = convert(
                NewContactMessage.class,
                notification.message
        );

        final Contact contact = mapper.mapContact(message.contact);

        return contactActions
                .createOrUpdateContact(contact)
                .doOnNext(ignored -> contactActions.showNewContactNotification(contact))
                .toCompletable();
    }

    private Completable handleContactUpdate(NotificationJson notification) {
        final ContactUpdateMessage message = convert(
                ContactUpdateMessage.class,
                notification.message
        );

        return contactActions
                .createOrUpdateContact(mapper.mapContact(message.contact))
                .toCompletable();
    }

    private Completable handleNewOperation(NotificationJson notification) {
        final NewOperationMessage message = convert(
                NewOperationMessage.class,
                notification.message
        );

        final Operation operation = mapper.mapOperation(message.operation);

        final NextTransactionSize nextTransactionSize = mapper
                .mapNextTransactionSize(message.nextTransactionSize);

        return operationActions
                .onRemoteOperationCreated(operation, nextTransactionSize)
                .toCompletable();
    }

    private Completable handleOperationUpdate(NotificationJson notification) {
        final OperationUpdateMessage message = convert(
                OperationUpdateMessage.class,
                notification.message
        );

        final NextTransactionSize nextTransactionSize = mapper
                .mapNextTransactionSize(message.nextTransactionSize);

        return operationActions
                .onRemoteOperationUpdated(
                        message.id,
                        message.confirmations,
                        message.hash,
                        message.status
                )
                .doOnNext(ignored ->
                        operationActions.onNextTransactionSizeUpdated(nextTransactionSize)
                )
                .toCompletable();
    }

    private Completable handleEmailVerified(NotificationJson notificationJson) {
        userActions.verifyEmail();

        return Completable.complete();
    }

    private Completable handleAuthorizedSignin(NotificationJson notificationJson) {
        signinActions.authorizeSignin();

        return Completable.complete();
    }

    private Completable handleAuthorizedChallengeUpdate(NotificationJson notificationJson) {
        final AuthorizeChallengeUpdateMessage message = convert(
                AuthorizeChallengeUpdateMessage.class,
                notificationJson.message
        );

        if (message.pendingUpdateJson.type == ChallengeType.PASSWORD) {
            userActions.authorizePasswordChange(message.pendingUpdateJson.uuid);
        }

        return Completable.complete();
    }

    @VisibleForTesting
    public void addHandler(String messageType,
                           SessionStatus minimumPermissionRequired,
                           Func1<NotificationJson, Completable> handler) {
        handlers.put(messageType, new NotificationHandler(minimumPermissionRequired, handler));
    }

    private <T extends Message> T convert(Class<T> messageType, Object data) {
        return SerializationUtils.convertUsingMapper(messageType, data);
    }

    private class NotificationHandler {
        private final SessionStatus permission;
        private final Func1<NotificationJson, Completable> handler;

        private NotificationHandler(SessionStatus permission,
                                    Func1<NotificationJson, Completable> handler) {
            this.permission = permission;
            this.handler = handler;
        }
    }
}
