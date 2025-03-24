package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.newop.PushTransactionSlowError;
import io.muun.apollo.domain.libwallet.FeeBumpRefreshPolicy;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.libwallet.LibwalletService;
import io.muun.apollo.domain.libwallet.model.SigningExpectations;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationCreated;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.tx.PartiallySignedTransaction;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.OperationStatus;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Preconditions;

import androidx.annotation.VisibleForTesting;
import libwallet.Libwallet;
import libwallet.MusigNonces;
import libwallet.Transaction;
import rx.Observable;

import java.util.ArrayList;
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

    private final LibwalletService libwalletService;

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
                               OperationMetadataMapper operationMetadataMapper,
                               LibwalletService libwalletService) {

        this.createOperation = createOperation;
        this.userRepository = userRepository;
        this.keysRepository = keysRepository;
        this.publicProfileDao = publicProfileDao;
        this.houstonClient = houstonClient;
        this.contactActions = contactActions;
        this.operationMapper = operationMetadataMapper;
        this.libwalletService = libwalletService;
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

        final int maxAlternativeTransactions;
        if (prepPayment.swap != null
                && !prepPayment.swap.isLend()
                && prepPayment.swap.getFundingOutput().getConfirmationsNeeded() == 0
                && prepPayment.swap.getMaxAlternativeTransactions() != null) {
            maxAlternativeTransactions = prepPayment.swap.getMaxAlternativeTransactions();
        } else {
            maxAlternativeTransactions = 0;
        }

        final MusigNonces musigNonces = Libwallet.generateMusigNonces(outpoints.size());
        final List<MusigNonces> alternativeTxNonces = new ArrayList<>();
        for (var i = 0; i < maxAlternativeTransactions; i++) {
            alternativeTxNonces.add(Libwallet.generateMusigNonces(outpoints.size()));
        }

        return houstonClient.newOperation(
                opWithMetadata,
                outpoints,
                musigNonces,
                alternativeTxNonces
        ).flatMap(opCreated -> {

            Preconditions.checkArgument(
                    opCreated.alternativeTransactions.size() <= maxAlternativeTransactions
            );

            final Operation houstonOp =
                    operationMapper.mapFromMetadata(opCreated.operation);

            final String transactionHex;
            final List<String> alternativeTransactionsHex;

            if (op.isLendingSwap()) {
                // money was lent, involves no actual transaction
                transactionHex = null;
                alternativeTransactionsHex = null;
            } else {
                final var userPrivKey = keysRepository.getBasePrivateKey().toBlocking().first();

                transactionHex = signToHex(
                        op,
                        userPrivKey,
                        musigNonces,
                        buildSigningExpectations(op, opCreated, false),
                        opCreated.partiallySignedTransaction
                );

                alternativeTransactionsHex = new ArrayList<>();
                for (int i = 0; i < opCreated.alternativeTransactions.size(); i++) {

                    alternativeTransactionsHex.add(
                            signToHex(
                                    op,
                                    userPrivKey,
                                    alternativeTxNonces.get(i),
                                    buildSigningExpectations(op, opCreated, true),
                                    opCreated.alternativeTransactions.get(i)
                            )
                    );
                }
            }

            // Maybe Houston identified the receiver for us:
            final Operation mergedOperation = op.mergeWithUpdate(houstonOp);

            return houstonClient.pushTransactions(
                            transactionHex,
                            alternativeTransactionsHex,
                            houstonOp.getHid()
                    )
                    .flatMap(txPushed -> {
                        // Maybe Houston updated the operation status:
                        mergedOperation.status = txPushed.operation.getStatus();

                        libwalletService.persistFeeBumpFunctions(
                                txPushed.feeBumpFunctions,
                                FeeBumpRefreshPolicy.NTS_CHANGED
                        );

                        return createOperation.action(
                                mergedOperation, txPushed.nextTransactionSize
                        );
                    })
                    .onErrorResumeNext(t -> {
                        if (ExtensionsKt.isInstanceOrIsCausedByTimeoutError(t)) {

                            // Most times, a timeout just means that the tx will
                            // eventually be pushed, albeit slightly delayed. This
                            // way the app stores the operation/payment and can
                            // update its state once it receives a notification
                            mergedOperation.status = OperationStatus.FAILED;

                            return createOperation.saveOperation(mergedOperation)
                                    .flatMap(operation ->
                                            Observable.error(
                                                    new PushTransactionSlowError(t)
                                            )
                                    );
                        } else {
                            return Observable.error(t);
                        }
                    });
        });
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
            final MusigNonces musigNonces,
            final SigningExpectations signingExpectations,
            final PartiallySignedTransaction partiallySignedTransaction
    ) {

        final PublicKey baseMuunPublicKey = keysRepository
                .getBaseMuunPublicKey();

        // Produce the signed Bitcoin transaction:
        final Transaction txInfo = LibwalletBridge.sign(
                baseUserPrivateKey,
                baseMuunPublicKey,
                partiallySignedTransaction,
                Globals.INSTANCE.getNetwork(),
                musigNonces,
                signingExpectations
        );

        // Update the Operation after signing:
        operation.hash = txInfo.getHash();

        // Encode signed transaction:
        return Encodings.bytesToHex(txInfo.getBytes());
    }

    private static SigningExpectations buildSigningExpectations(
            final Operation operation,
            final OperationCreated operationCreated,
            boolean isAlternativeTx
    ) {
        final long outputAmount = operation.swap != null
                ? operation.swap.getFundingOutput().getOutputAmountInSatoshis()
                : operation.amount.inSatoshis;

        return new SigningExpectations(
                operation.receiverAddress,
                outputAmount,
                operationCreated.changeAddress,
                operation.fee.inSatoshis,
                isAlternativeTx
        );
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
