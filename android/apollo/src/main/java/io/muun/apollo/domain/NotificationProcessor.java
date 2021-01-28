package io.muun.apollo.domain;


import io.muun.apollo.data.net.ModelObjectsMapper;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.SigninActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.incoming_swap.FulfillIncomingSwapAction;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.OperationMetadataMapper;
import io.muun.apollo.domain.action.operation.UpdateOperationAction;
import io.muun.apollo.domain.errors.MessageOriginError;
import io.muun.apollo.domain.errors.MessagePermissionsError;
import io.muun.apollo.domain.errors.UnknownNotificationTypeError;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUpdated;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.common.Optional;
import io.muun.common.api.beam.notification.NotificationJson;
import io.muun.common.api.messages.AuthorizeChallengeUpdateMessage;
import io.muun.common.api.messages.AuthorizeRcSigninMessage;
import io.muun.common.api.messages.AuthorizeSigninMessage;
import io.muun.common.api.messages.ContactUpdateMessage;
import io.muun.common.api.messages.EmailVerifiedMessage;
import io.muun.common.api.messages.FulfillIncomingSwapMessage;
import io.muun.common.api.messages.Message;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.api.messages.NewContactMessage;
import io.muun.common.api.messages.NewOperationMessage;
import io.muun.common.api.messages.OperationUpdateMessage;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.model.SessionStatus;

import androidx.annotation.VisibleForTesting;
import rx.Completable;
import rx.functions.Func1;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

public class NotificationProcessor {

    private final UpdateOperationAction updateOperation;
    private final CreateOperationAction createOperation;

    private final ContactActions contactActions;
    private final UserActions userActions;
    private final SigninActions signinActions;

    private final ModelObjectsMapper mapper;
    private final OperationMetadataMapper operationMapper;
    private final FulfillIncomingSwapAction fulfillIncomingSwap;

    private final Map<String, NotificationHandler> handlers =
            new HashMap<>();

    /**
     * Constructor.
     */
    @Inject
    public NotificationProcessor(UpdateOperationAction updateOperation,
                                 CreateOperationAction createOperation,
                                 ContactActions contactActions,
                                 UserActions userActions,
                                 SigninActions signinActions,
                                 ModelObjectsMapper mapper,
                                 OperationMetadataMapper operationMapper,
                                 FulfillIncomingSwapAction fulfillIncomingSwap) {

        this.updateOperation = updateOperation;
        this.createOperation = createOperation;
        this.contactActions = contactActions;
        this.userActions = userActions;
        this.signinActions = signinActions;
        this.mapper = mapper;
        this.operationMapper = operationMapper;
        this.fulfillIncomingSwap = fulfillIncomingSwap;


        addHandler(NewContactMessage.SPEC, this::handleNewContact);

        addHandler(ContactUpdateMessage.SPEC, this::handleContactUpdate);

        addHandler(NewOperationMessage.SPEC, this::handleNewOperation);

        addHandler(OperationUpdateMessage.SPEC, this::handleOperationUpdate);

        addHandler(EmailVerifiedMessage.SPEC, this::handleEmailVerified);

        addHandler(AuthorizeSigninMessage.SPEC, this::handleAuthorizedSignin);

        addHandler(AuthorizeRcSigninMessage.SPEC, this::handleAuthorizedSignin);

        addHandler(AuthorizeChallengeUpdateMessage.SPEC, this::handleAuthChallengeUpdate);

        addHandler(FulfillIncomingSwapMessage.SPEC, this::handleFulfillincomingSwap);

    }

    /**
     * Process a notification, invoking the relevant handler.
     */
    public Completable process(NotificationJson notification) {

        return Completable.defer(() -> {
            final NotificationHandler notificationHandler = handlers.get(notification.messageType);

            if (notificationHandler == null) {
                // While we should avoid sending notifications to a client with a version that
                // does not support them, this can eventually happen during complex upgrades or
                // migrations.
                throw new UnknownNotificationTypeError(notification.messageType);
            }

            verifyPermissions(notification, notificationHandler.spec);
            verifyOrigin(notification, notificationHandler.spec);

            return notificationHandler.handler.call(notification);
        });
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

        final OperationWithMetadata operationWithMetadata = mapper.mapOperation(message.operation);
        final Operation operation = operationMapper.mapFromMetadata(operationWithMetadata);

        final NextTransactionSize nextTransactionSize = mapper
                .mapNextTransactionSize(message.nextTransactionSize);

        return createOperation.action(operation, nextTransactionSize).toCompletable();
    }

    private Completable handleOperationUpdate(NotificationJson notification) {
        final OperationUpdateMessage message = convert(
                OperationUpdateMessage.class,
                notification.message
        );

        final NextTransactionSize nextTransactionSize = mapper
                .mapNextTransactionSize(message.nextTransactionSize);

        final SubmarineSwap submarineSwap = mapper.mapSubmarineSwap(message.swapDetails);

        final OperationUpdated operationUpdated = new OperationUpdated(
                message.id,
                message.confirmations,
                message.hash,
                message.status,
                nextTransactionSize,
                submarineSwap);

        return updateOperation
                .action(operationUpdated)
                .toCompletable();
    }

    private Completable handleEmailVerified(NotificationJson notificationJson) {
        userActions.verifyEmail();

        return Completable.complete();
    }

    private Completable handleAuthorizedSignin(NotificationJson notificationJson) {
        signinActions.reportAuthorizedByEmail();

        return Completable.complete();
    }

    private Completable handleAuthChallengeUpdate(NotificationJson notificationJson) {
        final AuthorizeChallengeUpdateMessage message = convert(
                AuthorizeChallengeUpdateMessage.class,
                notificationJson.message
        );

        if (message.pendingUpdateJson.type == ChallengeType.PASSWORD) {
            userActions.authorizePasswordChange(message.pendingUpdateJson.uuid);
        }

        return Completable.complete();
    }

    private Completable handleFulfillincomingSwap(final NotificationJson notificationJson) {
        final FulfillIncomingSwapMessage message = convert(
                FulfillIncomingSwapMessage.class,
                notificationJson.message
        );

        return fulfillIncomingSwap.action(message.uuid).toCompletable();
    }

    private void verifyPermissions(NotificationJson notification, MessageSpec spec) {
        final Optional<SessionStatus> status = signinActions.getSessionStatus();

        if (!status.isPresent() || !status.get().hasPermissionFor(spec.permission)) {
            throw new MessagePermissionsError(
                    notification.senderSessionUuid,
                    notification.id,
                    status.orElse(null),
                    spec
            );
        }
    }

    private void verifyOrigin(NotificationJson notification, MessageSpec spec) {

        // 1. Can this message come from anywhere?
        if (spec.allowedOrigin == MessageOrigin.ANY) {
            return;
        }

        final MessageOrigin origin = MessageOrigin.HOUSTON;

        // 2. Is the message allowed from this origin?
        if (origin != spec.allowedOrigin) {
            throw new MessageOriginError(
                    notification.senderSessionUuid,
                    notification.id,
                    origin,
                    spec
            );
        }
    }

    @VisibleForTesting
    public void addHandler(MessageSpec spec, Func1<NotificationJson, Completable> handler) {
        handlers.put(spec.messageType, new NotificationHandler(spec, handler));
    }

    private <T extends Message> T convert(Class<T> messageType, Object data) {
        return SerializationUtils.convertUsingMapper(messageType, data);
    }

}
