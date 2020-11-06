package io.muun.apollo.data.db.submarine_swap;

import io.muun.apollo.data.db.base.HoustonUuidDao;
import io.muun.apollo.data.db.operation.SubmarineSwapModel;
import io.muun.apollo.domain.model.SubmarineSwap;

import rx.Observable;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SubmarineSwapDao extends HoustonUuidDao<SubmarineSwap> {

    /**
     * Constructor.
     */
    @Inject
    public SubmarineSwapDao() {
        super(
                SubmarineSwapEntity.CREATE_TABLE,
                SubmarineSwapEntity::fromModel,
                SubmarineSwapEntity::toModel,
                SubmarineSwapEntity.TABLE_NAME
        );
    }

    /**
     * Fetches all operation swaps from the db.
     */
    public Observable<List<SubmarineSwap>> fetchAll() {
        return fetchList(SubmarineSwapEntity.FACTORY.selectAll());
    }

    /**
     * Updates the submarine swap payment data. Sets payedAt date and preimage.
     */
    public void updatePaymentInfo(SubmarineSwap swap) {
        final SubmarineSwapModel.UpdatePaymentInfo statement = new SubmarineSwapEntity
                .UpdatePaymentInfo(db, SubmarineSwapEntity.FACTORY);

        statement.bind(swap.getPayedAt(), swap.getPreimageInHex(), swap.houstonUuid);

        executeUpdate(statement);
    }
}
