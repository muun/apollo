package io.muun.apollo.data.db.incoming_swap;

import io.muun.apollo.data.db.base.HoustonUuidDao;
import io.muun.apollo.domain.model.IncomingSwapHtlc;

import rx.Observable;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class IncomingSwapHtlcDao extends HoustonUuidDao<IncomingSwapHtlc> {

    /**
     * Constructor.
     */
    @Inject
    public IncomingSwapHtlcDao() {
        super(
                IncomingSwapHtlcEntity.CREATE_TABLE,
                IncomingSwapHtlcEntity::fromModel,
                IncomingSwapHtlcEntity::toModel,
                IncomingSwapHtlcEntity.TABLE_NAME
        );
    }

    /**
     * Fetches all HTLCs from the db.
     */
    public Observable<List<IncomingSwapHtlc>> fetchAll() {
        return fetchList(IncomingSwapHtlcEntity.FACTORY.selectAll());
    }
}
