package io.muun.apollo.data.db.incoming_swap;

import io.muun.apollo.data.db.base.HoustonUuidDao;
import io.muun.apollo.domain.model.IncomingSwap;
import io.muun.apollo.domain.model.Sha256Hash;

import rx.Completable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class IncomingSwapDao extends HoustonUuidDao<IncomingSwap> {

    /**
     * Constructor.
     */
    @Inject
    public IncomingSwapDao() {
        super("incoming_swaps");
    }

    @Override
    public Completable deleteAll() {
        return Completable.fromAction(delightDb.getIncomingSwapQueries()::deleteAll);
    }

    @Override
    protected void storeUnsafe(final IncomingSwap element) {
        final Sha256Hash preimage = element.getPreimage();
        delightDb.getIncomingSwapQueries().insertIncomingSwap(
                element.getId(),
                element.houstonUuid,
                element.getPaymentHash().toString(),
                element.getSphinxPacketHex(),
                element.getCollectInSats(),
                element.getPaymentAmountInSats(),
                preimage != null ? preimage.toString() : null
        );
    }
}
