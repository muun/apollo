package io.muun.apollo.data.db.incoming_swap;

import io.muun.apollo.data.db.base.HoustonUuidDao;
import io.muun.apollo.domain.model.IncomingSwap;

import rx.Observable;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class IncomingSwapDao extends HoustonUuidDao<IncomingSwap> {

    /**
     * Constructor.
     */
    @Inject
    public IncomingSwapDao() {
        super(
                IncomingSwapEntity.CREATE_TABLE,
                IncomingSwapEntity::fromModel,
                IncomingSwapEntity::toModel,
                IncomingSwapEntity.TABLE_NAME
        );
    }

    /**
     * Fetches all incoming swaps from the db.
     */
    public Observable<List<IncomingSwap>> fetchAll() {
        return fetchList(IncomingSwapEntity.FACTORY.selectAll());
    }
}
