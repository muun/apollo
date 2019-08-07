package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.HardwareWalletActions;
import io.muun.apollo.domain.action.base.BaseAsyncAction2;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.HardwareWallet;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.common.crypto.hd.HardwareWalletAddress;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hwallet.HardwareWalletState;
import io.muun.common.crypto.tx.PartiallySignedTransaction;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.OperationStatus;

import android.support.annotation.VisibleForTesting;
import org.bitcoinj.core.Transaction;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SubmitOutgoingPaymentAction extends BaseAsyncAction2<
        PaymentRequest,
        PreparedPayment,
        Void> {

    private final CreateOperationAction createOperation;

    private final UserRepository userRepository;
    private final KeysRepository keysRepository;
    private final PublicProfileDao publicProfileDao;

    private final HoustonClient houstonClient;

    // TODO: remove this dependencies when actions are extracted.
    private final ContactActions contactActions;
    private final HardwareWalletActions hardwareWalletActions;

    /**
     * Submit an outgoing payment to Houston, and update local data in response.
     */
    @Inject
    public SubmitOutgoingPaymentAction(CreateOperationAction createOperation,
                                       UserRepository userRepository,
                                       KeysRepository keysRepository,
                                       PublicProfileDao publicProfileDao,
                                       HoustonClient houstonClient,
                                       ContactActions contactActions,
                                       HardwareWalletActions hardwareWalletActions) {

        this.createOperation = createOperation;
        this.userRepository = userRepository;
        this.keysRepository = keysRepository;
        this.publicProfileDao = publicProfileDao;
        this.houstonClient = houstonClient;
        this.contactActions = contactActions;
        this.hardwareWalletActions = hardwareWalletActions;
    }

    @Override
    public Observable<Void> action(PaymentRequest payReq, PreparedPayment prepPayment) {
        return Observable.defer(() -> submitPayment(payReq, prepPayment));
    }

    private Observable<Void> submitPayment(PaymentRequest payReq, PreparedPayment prepPayment) {
        final Operation operation = buildOperation(payReq, prepPayment);

        return Observable.defer(keysRepository::getBasePrivateKey)
                .flatMap(baseUserPrivateKey -> houstonClient.newOperation(operation)
                        .flatMap(operationCreated -> {
                            final PublicKey baseMuunPublicKey = keysRepository
                                    .getBaseMuunPublicKey();

                            // Extract data from response:
                            final Operation houstonOp = operationCreated.operation;

                            final PartiallySignedTransaction partiallySignedTransaction =
                                    operationCreated.partiallySignedTransaction;

                            partiallySignedTransaction.addUserSignatures(
                                    baseUserPrivateKey,
                                    baseMuunPublicKey
                            );

                            final Transaction fullySignedTransaction = partiallySignedTransaction
                                    .getTransaction();

                            operation.hash = fullySignedTransaction.getHashAsString();
                            operation.status = OperationStatus.SIGNED;

                            // Maybe Houston identified the receiver for us:
                            final Operation mergedOperation = operation.mergeWithUpdate(houstonOp);

                            return houstonClient
                                    .pushTransaction(fullySignedTransaction, houstonOp.getHid())
                                    .flatMap(txPushed -> createOperation.action(
                                            mergedOperation, txPushed.nextTransactionSize
                                    ));
                        }));
    }

    /**
     * Build an Operation.
     */
    @VisibleForTesting
    public Operation buildOperation(PaymentRequest payReq, PreparedPayment prepPayment) {

        switch (payReq.getType()) {
            case TO_CONTACT:
                return buildOperationToContact(payReq.getContact(), prepPayment);

            case TO_ADDRESS:
                return buildOperationToExternal(payReq.getAddress(), prepPayment);

            case TO_HARDWARE_WALLET:
                return buildOperationToHardwareWallet(payReq.getHardwareWallet(), prepPayment);

            case TO_LN_INVOICE:
                return buildOperationToLnInvoice(payReq.getSwap(), prepPayment);

            default:
                throw new MissingCaseError(payReq.getType());
        }
    }


    private Operation buildOperationToContact(Contact contact, PreparedPayment prepPayment) {

        final MuunAddress receiverAddress = contactActions.getAddressForContact(contact);

        return Operation.createOutgoing(
                userRepository.fetchOne().getCompatPublicProfile(),
                publicProfileDao.fetchOneByHid(contact.getHid()),
                null,
                receiverAddress.getAddress(),
                receiverAddress.getDerivationPath(),
                prepPayment.amount,
                prepPayment.fee,
                prepPayment.description,
                prepPayment.rateWindowHid
        );
    }

    private Operation buildOperationToExternal(String address, PreparedPayment prepPayment) {
        return Operation.createOutgoing(
                userRepository.fetchOne().getCompatPublicProfile(),
                null,
                null,
                address,
                null,
                prepPayment.amount,
                prepPayment.fee,
                prepPayment.description,
                prepPayment.rateWindowHid
        );
    }

    private Operation buildOperationToHardwareWallet(HardwareWallet hw,
                                                     PreparedPayment prepPayment) {

        final HardwareWalletState state = hardwareWalletActions.getHardwareWalletState(hw.getHid());
        final HardwareWalletAddress nextAddress = state.getNextAddress();

        return Operation.createOutgoing(
                userRepository.fetchOne().getCompatPublicProfile(),
                null,
                hw.getHid(),
                nextAddress.getAddress(),
                nextAddress.getDerivationPath(),
                prepPayment.amount,
                prepPayment.fee,
                prepPayment.description,
                prepPayment.rateWindowHid
        );
    }

    private Operation buildOperationToLnInvoice(SubmarineSwap swap,
                                                PreparedPayment prepPayment) {
        return Operation.createSwap(
                userRepository.fetchOne().getCompatPublicProfile(),
                prepPayment.amount,
                prepPayment.fee,
                prepPayment.description,
                prepPayment.rateWindowHid,
                swap
        );
    }
}
