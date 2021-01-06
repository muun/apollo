package io.muun.apollo.data.db.incoming_swap;

import io.muun.apollo.data.db.base.HoustonUuidDao;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class IncomingSwapHtlcDao extends HoustonUuidDao<IncomingSwapHtlcDb> {

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
}
