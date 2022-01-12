package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationCreated;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.OperationStatus;
import io.muun.common.utils.Encodings;

import androidx.annotation.VisibleForTesting;
import libwallet.Libwallet;
import libwallet.MusigNonces;
import libwallet.Transaction;
import rx.Observable;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class SubmitPaymentAction extends BaseAsyncAction1<
        PreparedPayment,
        Operation> {

    private final CreateOperationAction createOperation;

    private final UserRepository userRepository;
    private final KeysRepository keysRepository;
    private final PublicProfileDao publicProfileDao;

    private final HoustonClient houstonClient;

    // TODO: remove this dependencies when actions are extracted.
    private final ContactActions contactActions;
    private final OperationMetadataMapper operationMapper;

    /**
     * Submit an outgoing payment to Houston, and update local data in response.
     */
    @Inject
    public SubmitPaymentAction(CreateOperationAction createOperation,
                               UserRepository userRepository,
                               KeysRepository keysRepository,
                               PublicProfileDao publicProfileDao,
                               HoustonClient houstonClient,
                               ContactActions contactActions,
                               OperationMetadataMapper operationMetadataMapper) {

        this.createOperation = createOperation;
        this.userRepository = userRepository;
        this.keysRepository = keysRepository;
        this.publicProfileDao = publicProfileDao;
        this.houstonClient = houstonClient;
        this.contactActions = contactActions;
        this.operationMapper = operationMetadataMapper;
    }

    @Override
    public Observable<Operation> action(PreparedPayment prepPayment) {
        return Observable.defer(() -> submitPayment(prepPayment));
    }

    private Observable<Operation> submitPayment(PreparedPayment prepPayment) {

        final Operation op = buildOperation(prepPayment);
        final OperationWithMetadata opWithMetadata =
                buildOperationWithMetadata(prepPayment, op);

        final List<String> outpoints = prepPayment.outpoints;

        final int nonceCount = outpoints.size();
        final MusigNonces musigNonces = Libwallet.generateMusigNonces(nonceCount);

        return Observable.defer(keysRepository::getBasePrivateKey)
                .flatMap(baseUserPrivKey -> newOperation(opWithMetadata, outpoints, musigNonces)
                        .flatMap(opCreated -> {
                            final Operation houstonOp =
                                    operationMapper.mapFromMetadata(opCreated.operation);

                            final String transactionHex = op.isLendingSwap()
                                    ? null // money was lent, involves no actual transaction
                                    : signToHex(op, baseUserPrivKey, opCreated, musigNonces);

                            // Maybe Houston identified the receiver for us:
                            final Operation mergedOperation = op.mergeWithUpdate(houstonOp);

                            return houstonClient
                                    .pushTransaction(transactionHex, houstonOp.getHid())
                                    .flatMap(txPushed -> {
                                        // Maybe Houston updated the operation status:
                                        mergedOperation.status = txPushed.operation.getStatus();

                                        return createOperation.action(
                                                mergedOperation, txPushed.nextTransactionSize
                                        );
                                    });
                        }));
    }

    private Observable<OperationCreated> newOperation(OperationWithMetadata operationWithMetadata,
                                                      List<String> outpoints,
                                                      MusigNonces nonces) {
        return houstonClient.newOperation(operationWithMetadata, outpoints, nonces);
    }

    private OperationWithMetadata buildOperationWithMetadata(
            final PreparedPayment preparedPayment,
            final Operation operation
    ) {


        if (preparedPayment.type == PaymentRequest.Type.TO_CONTACT) {

            return operationMapper.mapWithMetadataForContact(
                    operation,
                    preparedPayment.contact
            );
        } else {
            return operationMapper.mapWithMetadata(operation);
        }
    }

    @NotNull
    private String signToHex(
            final Operation operation,
            final PrivateKey baseUserPrivateKey,
            final OperationCreated operationCreated,
            final MusigNonces musigNonces
    ) {

        final PublicKey baseMuunPublicKey = keysRepository
                .getBaseMuunPublicKey();

        // Extract data from response:
        final MuunAddress changeAddress = operationCreated.changeAddress;

        // Update the operation from Houston's response:
        operation.changeAddress = changeAddress;

        // Produce the signed Bitcoin transaction:
        final Transaction txInfo = LibwalletBridge.sign(
                operation,
                baseUserPrivateKey,
                baseMuunPublicKey,
                operationCreated.partiallySignedTransaction,
                Globals.INSTANCE.getNetwork(),
                musigNonces
        );

        // Update the Operation after signing:
        operation.hash = txInfo.getHash();
        operation.status = OperationStatus.SIGNED;

        // Encode signed transaction:
        return Encodings.bytesToHex(txInfo.getBytes());
    }

    /**
     * Build an Operation.
     */
    @VisibleForTesting
    public Operation buildOperation(PreparedPayment prepPayment) {

        switch (prepPayment.type) {
            case TO_CONTACT:
                return buildOperationToContact(prepPayment.contact, prepPayment);

            case TO_ADDRESS:
                return buildOperationToExternal(prepPayment.address, prepPayment);

            case TO_LN_INVOICE:
                return buildOperationToLnInvoice(prepPayment.swap, prepPayment);

            default:
                throw new MissingCaseError(prepPayment.type);
        }
    }

    private Operation buildOperationToContact(Contact contact, PreparedPayment prepPayment) {

        final MuunAddress receiverAddress = contactActions.getAddressForContact(contact);

        return Operation.createOutgoing(
                userRepository.fetchOne().getCompatPublicProfile(),
                publicProfileDao.fetchOneByHid(contact.getHid()),
                receiverAddress.getAddress(),
                receiverAddress.getDerivationPath(),
                prepPayment
        );
    }

    private Operation buildOperationToExternal(String address, PreparedPayment prepPayment) {
        return Operation.createOutgoing(
                userRepository.fetchOne().getCompatPublicProfile(),
                null,
                address,
                null,
                prepPayment
        );
    }

    private Operation buildOperationToLnInvoice(SubmarineSwap swap, PreparedPayment prepPayment) {
        return Operation.createSwap(
                userRepository.fetchOne().getCompatPublicProfile(),
                prepPayment,
                swap
        );
    }
}
