package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.incoming_swap.IncomingSwapDao;
import io.muun.apollo.data.db.incoming_swap.IncomingSwapHtlcDao;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.db.submarine_swap.SubmarineSwapDao;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction2;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.common.model.OperationDirection;
import io.muun.common.rx.ObservableFn;

import rx.Observable;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CreateOperationAction
        extends BaseAsyncAction2<Operation, NextTransactionSize, Operation> {

    private final TransactionSizeRepository transactionSizeRepository;
    private final OperationDao operationDao;
    private final PublicProfileDao publicProfileDao;
    private final SubmarineSwapDao submarineSwapDao;
    private final IncomingSwapDao incomingSwapDao;
    private final IncomingSwapHtlcDao incomingSwapHtlcDao;

    private final NotificationService notificationService;

    /**
     * Create a new Operation in the local database, and update the transaction size vector.
     */
    @Inject
    public CreateOperationAction(final TransactionSizeRepository transactionSizeRepository,
                                 final OperationDao operationDao,
                                 final PublicProfileDao publicProfileDao,
                                 final SubmarineSwapDao submarineSwapDao,
                                 final NotificationService notificationService,
                                 final IncomingSwapDao incomingSwapDao,
                                 final IncomingSwapHtlcDao incomingSwapHtlcDao) {

        this.transactionSizeRepository = transactionSizeRepository;
        this.operationDao = operationDao;
        this.publicProfileDao = publicProfileDao;
        this.submarineSwapDao = submarineSwapDao;
        this.notificationService = notificationService;
        this.incomingSwapDao = incomingSwapDao;
        this.incomingSwapHtlcDao = incomingSwapHtlcDao;
    }

    @Override
    public Observable<Operation> action(Operation operation,
                                        NextTransactionSize nextTransactionSize) {

        return saveOperation(operation)
                .map(savedOperation -> {
                    Timber.d("Updating next transaction size estimation");
                    transactionSizeRepository.setTransactionSize(nextTransactionSize);

                    if (savedOperation.direction == OperationDirection.INCOMING) {
                        notificationService.showNewOperationNotification(savedOperation);
                    }

                    return savedOperation;
                });
    }

    /**
     * Save an operation to the database.
     */
    public Observable<Operation> saveOperation(Operation operation) {
        return operationDao.fetchByHid(operation.getHid())
                .first()
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ElementNotFoundException.class,
                        error -> {
                            Observable<Operation> chain = Observable.just(operation);

                            if (operation.senderProfile != null) {
                                chain = chain.compose(ObservableFn.flatDoOnNext(ignored ->
                                        publicProfileDao.store(operation.senderProfile)
                                ));
                            }

                            if (operation.receiverProfile != null) {
                                chain = chain.compose(ObservableFn.flatDoOnNext(ignored ->
                                        publicProfileDao.store(operation.receiverProfile)
                                ));
                            }

                            if (operation.swap != null) {
                                chain = chain.compose(ObservableFn.flatDoOnNext(ignored ->
                                        submarineSwapDao.store(operation.swap)
                                ));
                            }

                            if (operation.incomingSwap != null) {
                                chain = chain.compose(ObservableFn.flatDoOnNext(ignored ->
                                        incomingSwapHtlcDao.store(operation.incomingSwap.getHtlc())
                                ));

                                chain = chain.compose(ObservableFn.flatDoOnNext(ignored ->
                                        incomingSwapDao.store(operation.incomingSwap)
                                ));
                            }

                            chain = chain.flatMap(operationDao::store);

                            return chain;
                        }
                ));
    }
}
