package io.muun.apollo.data.db.operation;

import io.muun.apollo.data.db.base.HoustonIdDao;
import io.muun.apollo.domain.model.Operation;
import io.muun.common.model.OperationStatus;

import rx.Observable;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class OperationDao extends HoustonIdDao<Operation> {

    /**
     * Constructor.
     */
    @Inject
    public OperationDao() {
        super(
                OperationEntity.CREATE_TABLE,
                OperationEntity::fromModel,
                OperationEntity::toModel,
                OperationEntity.TABLE_NAME
        );
    }

    /**
     * Update the operations status and confirmations.
     */
    public void updateStatus(long operationHid, long confirmations, String hash,
                             OperationStatus status) {

        final OperationModel.UpdateStatus statement =
                new OperationEntity.UpdateStatus(db, OperationEntity.FACTORY);

        statement.bind(confirmations, hash, status, operationHid);

        executeStatement(statement);
    }

    /**
     * Fetches all operations from the db.
     */
    public Observable<List<Operation>> fetchAll() {

        return fetchList(OperationEntity.FACTORY.selectAll());
    }

    /**
     * Fetches a single operation by its id.
     */
    public Observable<Operation> fetchById(long operationId) {

        return fetchOneOrFail(OperationEntity.FACTORY.selectById(operationId));
    }

    /**
     * Fetches a single operation by its Houston id.
     */
    public Observable<Operation> fetchByHid(long operationHid) {

        return fetchOneOrFail(OperationEntity.FACTORY.selectByHid(operationHid));
    }

    public Observable<Operation> fetchLatest() {
        return fetchOneOrFail(OperationEntity.FACTORY.selectLatest());
    }
}
