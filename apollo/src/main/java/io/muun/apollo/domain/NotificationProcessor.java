package io.muun.apollo.domain;


import io.muun.apollo.data.net.ModelObjectsMapper;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.SatelliteActions;
import io.muun.apollo.domain.action.SigninActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.UpdateOperationAction;
import io.muun.apollo.domain.errors.MessageFromExpiredPairingError;
import io.muun.apollo.domain.errors.MessageOriginError;
import io.muun.apollo.domain.errors.MessagePermissionsError;
import io.muun.apollo.domain.errors.UnknownNotificationTypeError;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.HardwareWallet;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUpdated;
import io.muun.apollo.domain.model.SatellitePairing;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.satellite.messages.AddHardwareWalletMessage;
import io.muun.apollo.domain.satellite.messages.CompletePairingAckMessage;
import io.muun.apollo.domain.satellite.messages.GetSatelliteStateMessage;
import io.muun.apollo.domain.satellite.messages.SatelliteStateMessage;
import io.muun.apollo.domain.satellite.messages.SessionTakeoverMessage;
import io.muun.apollo.domain.satellite.messages.WithdrawalResultMessage;
import io.muun.common.Optional;
import io.muun.common.api.beam.notification.NotificationJson;
import io.muun.common.api.messages.AuthorizeChallengeUpdateMessage;
import io.muun.common.api.messages.AuthorizeSigninMessage;
import io.muun.common.api.messages.ContactUpdateMessage;
import io.muun.common.api.messages.EmailVerifiedMessage;
import io.muun.common.api.messages.ExpiredSessionMessage;
import io.muun.common.api.messages.Message;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.api.messages.NewContactMessage;
import io.muun.common.api.messages.NewOperationMessage;
import io.muun.common.api.messages.OperationUpdateMessage;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.SessionStatus;

import android.support.annotation.VisibleForTesting;
import rx.Completable;
import rx.functions.Func1;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

public class NotificationProcessor {

    private final UpdateOperationAction updateOperation;
    private final CreateOperationAction createOperation;

    private final OperationActions operationActions;
    private final ContactActions contactActions;
    private final UserActions userActions;
    private final SigninActions signinActions;
    private final SatelliteActions satelliteActions;

    private final ModelObjectsMapper mapper;

    private final Map<String, NotificationHandler> handlers =
            new HashMap<>();

    /**
     * Constructor.
     */
    @Inject
    public NotificationProcessor(UpdateOperationAction updateOperation,
                                 CreateOperationAction createOperation,
                                 OperationActions operationActions,
                                 ContactActions contactActions,
                                 UserActions userActions,
                                 SigninActions signinActions,
                                 SatelliteActions satelliteActions,
                                 ModelObjectsMapper mapper) {

        this.updateOperation = updateOperation;
        this.createOperation = createOperation;
        this.operationActions = operationActions;
        this.contactActions = contactActions;
        this.userActions = userActions;
        this.signinActions = signinActions;
        this.satelliteActions = satelliteActions;
        this.mapper = mapper;


        addHandler(NewContactMessage.SPEC, this::handleNewContact);

        addHandler(ContactUpdateMessage.SPEC, this::handleContactUpdate);

        addHandler(NewOperationMessage.SPEC, this::handleNewOperation);

        addHandler(OperationUpdateMessage.SPEC, this::handleOperationUpdate);

        addHandler(EmailVerifiedMessage.SPEC, this::handleEmailVerified);

        addHandler(AuthorizeSigninMessage.SPEC, this::handleAuthorizedSignin);

        addHandler(AuthorizeChallengeUpdateMessage.SPEC, this::handleAuthChallengeUpdate);

        addHandler(CompletePairingAckMessage.SPEC, this::handleCompletePairingAck);

        addHandler(AddHardwareWalletMessage.SPEC, this::handleAddHardwareWallet);

        addHandler(WithdrawalResultMessage.SPEC, this::handleWithdrawalResult);

        addHandler(ExpiredSessionMessage.SPEC, this::handleExpiredSession);

        addHandler(SessionTakeoverMessage.SPEC, this::handleSessionTakeover);

        addHandler(GetSatelliteStateMessage.SPEC, this::handleGetSalliteState);

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
                // migrations, especially since Satellite came into the mix.
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

        final Operation operation = mapper.mapOperation(message.operation);

        final NextTransactionSize nextTransactionSize = mapper
                .mapNextTransactionSize(message.nextTransactionSize);

        createOperation.run(operation, nextTransactionSize);

        return createOperation.getCompletion();
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

        updateOperation.run(operationUpdated);

        return updateOperation.getCompletion();
    }

    private Completable handleEmailVerified(NotificationJson notificationJson) {
        userActions.verifyEmail();

        return Completable.complete();
    }

    private Completable handleAuthorizedSignin(NotificationJson notificationJson) {
        signinActions.authorizeSignin();

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

    private Completable handleCompletePairingAck(NotificationJson notification) {
        final CompletePairingAckMessage message = convert(
                CompletePairingAckMessage.class,
                notification.message
        );

        satelliteActions.completePairingAction.run(
                notification.senderSessionUuid,
                message.browser,
                message.osVersion,
                message.ip
        );

        return Completable.complete();
    }

    private Completable handleAddHardwareWallet(NotificationJson notification) {
        final AddHardwareWalletMessage message = convert(
                AddHardwareWalletMessage.class,
                notification.message
        );

        final HardwareWallet hardwareWallet = new HardwareWallet(
                message.brand,
                message.model,
                message.label,
                PublicKey.deserializeFromBase58(
                        message.basePublicKeyPath,
                        message.basePublicKey
                ),
                true
        );

        satelliteActions.addWalletCompletedSignal.reset();
        satelliteActions.addWalletCompletedSignal.run(hardwareWallet);

        return Completable.complete();
    }

    private Completable handleWithdrawalResult(NotificationJson notification) {
        final WithdrawalResultMessage message = convert(
                WithdrawalResultMessage.class,
                notification.message
        );
        operationActions.submitSignedWithdrawalAction.reset();
        operationActions.submitSignedWithdrawalAction.run(message.uuid, message.signedTransaction);
        return Completable.complete();
    }

    private Completable handleExpiredSession(NotificationJson notification) {
        final ExpiredSessionMessage message = convert(
                ExpiredSessionMessage.class,
                notification.message
        );

        satelliteActions.reportSessionExpiredAction.reset();
        satelliteActions.reportSessionExpiredAction.run(message.expiredSessionUuid);

        return Completable.complete();
    }

    private Completable handleSessionTakeover(NotificationJson notification) {
        satelliteActions.reportSessionTakeoverAction.reset();
        satelliteActions.reportSessionTakeoverAction.run(notification.senderSessionUuid);

        return Completable.complete();
    }

    private Completable handleGetSalliteState(NotificationJson notification) {
        final SatelliteStateMessage message = convert(
                SatelliteStateMessage.class,
                notification.message
        );

        satelliteActions.resendSatelliteStateAction.reset();
        satelliteActions.resendSatelliteStateAction.run(notification.senderSessionUuid);

        return Completable.complete();
    }

    private void verifyPermissions(NotificationJson notification, MessageSpec spec) {
        final Optional<SessionStatus> status = signinActions.getSessionStatus();

        if (!status.isPresent() || !status.get().hasPermisionFor(spec.permission)) {
            throw new MessagePermissionsError(
                    notification.senderSessionUuid,
                    notification.id,
                    status.orElse(null),
                    spec
            );
        }
    }

    private void verifyOrigin(NotificationJson notification, MessageSpec spec) {
        // TODO:
        // We use a blacklist approach to verify origins. If a message does not come from any
        // known Satellite, we assume it's from Houston. This is bug-prone, and we should instead
        // know Houston's sender session UUID. Consider:

        // If Satellite sends a message to be delivered via GCM, and then the pairing is expired,
        // there's a chance that the message (already in-flight) arrives after the fact. If we
        // have no remaining record of that pairing, we can confuse Satellite for Houston (bad).

        // We should be safe as long as we:
        // 1. Don't delete pairings right away (marking them as EXPIRED instead).
        // 2. Verify that they are ACTIVE before processing a message.

        // --- See https://bit.ly/2QO409j ---

        // 1. Can this message come from anywhere?
        if (spec.allowedOrigin == MessageOrigin.ANY) {
            return;
        }

        // 2. Did this come from a known Satellite, or should we assume Houston?
        final Optional<SatellitePairing> senderPairing = satelliteActions
                .findPairingByApolloSession(notification.senderSessionUuid);

        final MessageOrigin origin = senderPairing.isPresent()
                ? MessageOrigin.SATELLITE
                : MessageOrigin.HOUSTON;

        // 3. Is the message allowed from this origin?
        if (origin != spec.allowedOrigin) {
            throw new MessageOriginError(
                    notification.senderSessionUuid,
                    notification.id,
                    origin,
                    spec
            );
        }

        // 4. If Satellite, is the pairing active?
        if (senderPairing.isPresent() && senderPairing.get().isExpired()) {
            throw new MessageFromExpiredPairingError(
                    notification.senderSessionUuid,
                    notification.id,
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
