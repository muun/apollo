package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.submarine_swap.SubmarineSwapDao;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUpdated;
import io.muun.common.model.OperationStatus;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateOperationAction extends BaseAsyncAction1<OperationUpdated, Void> {

    private final TransactionSizeRepository transactionSizeRepository;
    private final OperationDao operationDao;
    private final SubmarineSwapDao submarineSwapDao;

    private final NotificationService notificationService;

    /**
     * Update an Operation, given fresh data from Houston.
     */
    @Inject
    public UpdateOperationAction(TransactionSizeRepository transactionSizeRepository,
                                 OperationDao operationDao,
                                 SubmarineSwapDao submarineSwapDao,
                                 NotificationService notificationService) {

        this.transactionSizeRepository = transactionSizeRepository;
        this.operationDao = operationDao;
        this.submarineSwapDao = submarineSwapDao;
        this.notificationService = notificationService;
    }

    @Override
    public Observable<Void> action(OperationUpdated operationUpdated) {
        return Observable.fromCallable(() -> {
            final Operation previousOp = operationDao.fetchByHid(operationUpdated.hid)
                    .toBlocking()
                    .first();

            if (hasTransitionedToFailure(previousOp, operationUpdated)) {
                notificationService.showOperationFailedNotification(previousOp.getId());
            }

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

    private boolean hasTransitionedToFailure(Operation previousOp, OperationUpdated update) {
        final boolean wasFailed = shouldConsideredFailed(previousOp.status);
        final boolean isFailed = shouldConsideredFailed(update.status);

        return isFailed && !wasFailed;
    }

    private boolean shouldConsideredFailed(OperationStatus status) {
        switch (status) {
            case FAILED:
            case DROPPED:
            case SWAP_FAILED:
                return true;

            default:
                return false;
        }
    }
}
