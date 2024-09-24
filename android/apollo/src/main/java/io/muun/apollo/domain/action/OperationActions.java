package io.muun.apollo.domain.action;

import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.OperationMetadataMapper;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.common.rx.RxHelper;

import rx.Observable;
import timber.log.Timber;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class OperationActions {

    private final CreateOperationAction createOperation;
    private final OperationDao operationDao;
    private final HoustonClient houstonClient;
    private final OperationMetadataMapper operationMapper;


    /**
     * Constructor.
     */
    @Inject
    public OperationActions(
            CreateOperationAction createOperation,
            OperationDao operationDao,
            HoustonClient houstonClient,
            OperationMetadataMapper operationMapper
    ) {

        Timber.d("[OperationActions] Execute Dependency Injection");

        this.createOperation = createOperation;
        this.operationDao = operationDao;
        this.houstonClient = houstonClient;
        this.operationMapper = operationMapper;
    }

    /**
     * Fetch the complete operation list from Houston.
     */
    public Observable<Void> fetchReplaceOperations() {
        Timber.d("[Operations] Fetching full operation list");

        return operationDao.deleteAll().andThen(
                houstonClient.fetchOperations()
                        .doOnNext(list -> Timber.i("Received op history: size " + list.size()))
                        .buffer(20)
                        .flatMap(chunks -> {

                            // Hack warning: we can't use flatMap (parallelization breaks
                            // JobExecutor threadpool capacity) nor concatMap (suddenly stops
                            // processing events from Observable.from() silently) directly.
                            // So we introduce some "parallel processing by batches".
                            // TODO we should really migrate to using batching Sqlite inserts
                            //  instead of inserting one-by-one

                            for (List<OperationWithMetadata> chunk : chunks) {
                                if (!chunk.isEmpty()) {
                                    Observable.from(chunk)
                                            .doOnNext(op -> Timber.i("processing op:" + op.getId()))
                                            .map(operationMapper::mapFromMetadata)
                                            .flatMap(createOperation::saveOperation, 5)
                                            .toBlocking()
                                            .last();
                                }
                            }

                            return Observable.just(null);
                        })
                        .lastOrDefault(null)
                        .map(RxHelper::toVoid)
        );
    }

    /**
     * Fetches a single operation from the database, by id.
     */
    public Observable<Operation> fetchOperationById(Long operationId) {
        return operationDao.fetchById(operationId);
    }
}
