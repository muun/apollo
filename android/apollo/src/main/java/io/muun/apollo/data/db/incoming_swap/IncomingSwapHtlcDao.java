package io.muun.apollo.data.db.incoming_swap;

import io.muun.apollo.data.db.base.HoustonUuidDao;
import io.muun.apollo.domain.model.IncomingSwapHtlc;
import io.muun.common.utils.Encodings;

import rx.Completable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class IncomingSwapHtlcDao extends HoustonUuidDao<IncomingSwapHtlcDb> {

    /**
     * Constructor.
     */
    @Inject
    public IncomingSwapHtlcDao() {
        super("incoming_swap_htlcs");
    }

    @Override
    public Completable deleteAll() {
        return Completable.fromAction(delightDb.getIncomingSwapHtlcQueries()::deleteAll);
    }

    @Override
    protected void storeUnsafe(final IncomingSwapHtlcDb element) {
        final IncomingSwapHtlc htlc = element.getHtlc();
        final byte[] fulfillmentTx = htlc.getFulfillmentTx();

        delightDb.getIncomingSwapHtlcQueries().insertIncomingSwapHtlc(
                element.getId(),
                element.houstonUuid,
                htlc.getExpirationHeight(),
                htlc.getFulfillmentFeeSubsidyInSats(),
                htlc.getLentInSats(),
                Encodings.bytesToHex(htlc.getSwapServerPublicKey()),
                fulfillmentTx != null ? Encodings.bytesToHex(fulfillmentTx) : null,
                htlc.getAddress(),
                htlc.getOutputAmountInSatoshis(),
                Encodings.bytesToHex(htlc.getHtlcTx()),
                element.getSwapHoustonUuid()
        );

    }
}
