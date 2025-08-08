package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.submarine_swap.SubmarineSwapDao;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationUpdated;
import io.muun.common.model.OperationStatus;
import io.muun.common.utils.Preconditions;

import rx.Observable;
import timber.log.Timber;

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
    public UpdateOperationAction(
            TransactionSizeRepository transactionSizeRepository,
            OperationDao operationDao,
            SubmarineSwapDao submarineSwapDao,
            NotificationService notificationService
    ) {

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

            Preconditions.checkNotNull(previousOp); // FetchByHid returns non null (or throws).
            Preconditions.checkNotNull(previousOp.getId()); // DB stored op HAS an id

            if (hasTransitionedToFailure(previousOp, operationUpdated)) {
                notificationService.showOperationFailedNotification(previousOp.getId());
            }

            operationDao.updateStatus(
                    operationUpdated.hid,
                    operationUpdated.confirmations,
                    operationUpdated.hash,
                    operationUpdated.status
            );

            final NextTransactionSize prevNts = transactionSizeRepository.getNextTransactionSize();
            transactionSizeRepository.setTransactionSize(operationUpdated.nextTransactionSize);
            //noinspection StatementWithEmptyBody
            if (shouldUpdateNts(operationUpdated, prevNts)) {
                // TODO setTransactionSize should be here
            } else {
                Timber.e(
                        "Stale NTS update at UpdateOperation op.Hid:%s. %s vs %s",
                        operationUpdated.hid,
                        operationUpdated.nextTransactionSize.validAtOperationHid,
                        prevNts.validAtOperationHid
                );
            }

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

    /**
     * We should update NTS if updated operation is equal to or newer than the latest known
     * operation we have stored. Else, we're receiving a stale update.
     */
    private boolean shouldUpdateNts(OperationUpdated operation, NextTransactionSize currentNts) {

        if (currentNts == null || currentNts.validAtOperationHid == null) {
            return true; // we have no NTS or a really old one.
        }

        // We allow nts updates related to the same operation
        return operation.hid >= currentNts.validAtOperationHid;
    }
}
