package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction0;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.apollo.domain.model.Operation;
import io.muun.common.Optional;
import io.muun.common.rx.ObservableFn;

import rx.Observable;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FetchNextTransactionSizeAction extends BaseAsyncAction0<NextTransactionSize> {

    private final OperationDao operationDao;
    private final TransactionSizeRepository transactionSizeRepository;
    private final HoustonClient houstonClient;

    /**
     * Fetch the NextTransactionSize vector, only if the local one is stale.
     */
    @Inject
    public FetchNextTransactionSizeAction(OperationDao operationDao,
                                          TransactionSizeRepository transactionSizeRepository,
                                          HoustonClient houstonClient) {

        this.operationDao = operationDao;
        this.transactionSizeRepository = transactionSizeRepository;
        this.houstonClient = houstonClient;
    }

    @Override
    public Observable<NextTransactionSize> action() {
        return Observable.defer(this::fetchNextTransactionSize);
    }

    private Observable<NextTransactionSize> fetchNextTransactionSize() {
        final NextTransactionSize nextTransactionSize = transactionSizeRepository
                .getNextTransactionSize();

        if (! isTransactionSizeStale(nextTransactionSize)) {
            return Observable.just(nextTransactionSize);
        }

        return houstonClient.fetchNextTransactionSize()
                .doOnNext(newNextTransactionSize -> {
                    Logger.debug("Updating next transaction size estimation");
                    transactionSizeRepository.setTransactionSize(newNextTransactionSize);
                });
    }

    private boolean isTransactionSizeStale(@Nullable NextTransactionSize nextTransactionSize) {
        if (nextTransactionSize == null) {
            return true;
        }

        final long validAtOperationHid = Optional
                .ofNullable(nextTransactionSize.validAtOperationHid)
                .orElse(0L);

        final long latestOperationHid = getLatestOperation()
                .map(latestOperation -> latestOperation.hid)
                .orElse(0L);

        // NOTE: if an Operation has been made, giving us new UTXOs (and thus
        // affecting the values of NextTransactionSize) but we haven't received the
        // notification yet, it may happen that validAtOperationHid >
        // latestOperationHid. In other words, nextTransactionSize may be more recent
        // than latestOperation if we pulled it manually.

        // We'll allow that, considering it valid. This is not ideal, but all of this
        // will go away once the wallet uses SPV. Good enough for now.
        if (validAtOperationHid < latestOperationHid) {
            return true;
        }

        return false;
    }


    private Optional<Operation> getLatestOperation() {
        return operationDao.fetchLatest()
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ElementNotFoundException.class,
                        error -> Observable.just(null)
                ))
                .map(Optional::ofNullable)
                .toBlocking()
                .first();
    }

}
