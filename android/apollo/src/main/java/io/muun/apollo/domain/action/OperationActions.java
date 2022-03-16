package io.muun.apollo.domain.action;

import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.OperationMetadataMapper;
import io.muun.apollo.domain.model.Operation;
import io.muun.common.rx.RxHelper;

import rx.Observable;
import timber.log.Timber;

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
    public OperationActions(CreateOperationAction createOperation,
                            OperationDao operationDao,
                            HoustonClient houstonClient,
                            OperationMetadataMapper operationMapper) {

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
                        .flatMap(Observable::from)
                        // using concatMap to avoid parallelization, overflows JobExecutor's queue
                        // TODO use batching
                        .map(operationMapper::mapFromMetadata)
                        .concatMap(createOperation::saveOperation)
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
