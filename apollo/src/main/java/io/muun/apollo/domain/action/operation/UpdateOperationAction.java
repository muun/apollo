package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.submarine_swap.SubmarineSwapDao;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.OperationUpdated;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateOperationAction extends BaseAsyncAction1<OperationUpdated, Void> {

    private final TransactionSizeRepository transactionSizeRepository;
    private final OperationDao operationDao;
    private final SubmarineSwapDao submarineSwapDao;

    /**
     * Update an Operation, given fresh data from Houston.
     */
    @Inject
    public UpdateOperationAction(TransactionSizeRepository transactionSizeRepository,
                                 OperationDao operationDao,
                                 SubmarineSwapDao submarineSwapDao) {

        this.transactionSizeRepository = transactionSizeRepository;
        this.operationDao = operationDao;
        this.submarineSwapDao = submarineSwapDao;
    }

    @Override
    public Observable<Void> action(OperationUpdated operationUpdated) {
        return Observable.fromCallable(() -> {
            operationDao.updateStatus(
                    operationUpdated.hid,
                    operationUpdated.confirmations,
                    operationUpdated.hash,
                    operationUpdated.status
            );

            transactionSizeRepository.setTransactionSize(operationUpdated.nextTransactionSize);

            if (operationUpdated.submarineSwap != null) {
                submarineSwapDao.updatePaymentInfo(operationUpdated.submarineSwap);
            }

            return null; // meh
        });
    }
}
