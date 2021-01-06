package io.muun.apollo.data.db.operation;

import io.muun.apollo.data.db.base.HoustonIdDao;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.selector.UtxoSetStateSelector;
import io.muun.common.Optional;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;
import io.muun.common.utils.Pair;

import com.squareup.sqldelight.prerelease.SqlDelightQuery;
import org.jetbrains.annotations.NotNull;
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
    public void updateStatus(long operationHid,
                             long confirmations,
                             String hash,
                             OperationStatus status) {

        final OperationModel.UpdateStatus statement =
                new OperationEntity.UpdateStatus(db, OperationEntity.FACTORY);

        statement.bind(confirmations, hash, status, operationHid);

        executeUpdate(statement);
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
        return fetchOneOrFail(OperationEntity.FACTORY.selectById(operationId))
                .doOnError(error -> enhanceError(error, String.valueOf(operationId)));
    }

    /**
     * Fetches a single operation by its Houston id.
     */
    public Observable<Operation> fetchByHid(long operationHid) {
        return fetchOneOrFail(OperationEntity.FACTORY.selectByHid(operationHid))
                .doOnError(error -> enhanceError(error, String.valueOf(operationHid)));
    }

    public Observable<Operation> fetchLatest() {
        return fetchOneOrFail(OperationEntity.FACTORY.selectLatest())
                .doOnError(error -> enhanceError(error, "null (latest)"));
    }

    public Observable<Optional<Operation>> fetchMaybeLatest() {
        return fetchMaybeOne(OperationEntity.FACTORY.selectLatest());
    }

    public Observable<List<Operation>> fetchUnsettled() {
        return fetchList(OperationEntity.FACTORY.selectUnsettled());
    }

    public Observable<Operation> fetchByIncomingSwapUuid(final String incomingSwap) {
        return fetchOneOrFail(OperationEntity.FACTORY.selectByIncomingSwap(incomingSwap));
    }

    @NotNull
    public Observable<UtxoSetStateSelector.UtxoSetState> watchUtxoSetState() {
        final OperationStatus[] pendingStatus = Operation.PENDING_STATUS
                .toArray(new OperationStatus[0]);

        final SqlDelightQuery hasRbfQuery = OperationEntity.FACTORY.countPendingOps(
                OperationDirection.INCOMING, true, pendingStatus
        );
        final SqlDelightQuery hasNonRbfQuery = OperationEntity.FACTORY.countPendingOps(
                OperationDirection.INCOMING, false, pendingStatus
        );

        return Observable.zip(
                executeCount(hasRbfQuery),
                executeCount(hasNonRbfQuery),
                Pair::of
        ).map(pair -> {
            if (pair.fst > 0) {
                return UtxoSetStateSelector.UtxoSetState.RBF;
            } else if (pair.snd > 0) {
                return UtxoSetStateSelector.UtxoSetState.PENDING;
            } else {
                return UtxoSetStateSelector.UtxoSetState.CONFIRMED;
            }
        });
    }

}
