package io.muun.apollo.data.db.incoming_swap

import io.muun.apollo.domain.model.IncomingSwapHtlc
import io.muun.apollo.domain.model.base.HoustonUuidModel

/*
 * Well you might wonder why this exists!
 * The HoustonUuidDao / BaseDao assume that the model itself is enough to store in the DB. For most
 * models this is ok, but Htlcs have a foreign key to the swap, but the model itself doesn't. We'd
 * like to pass it as parameter or have the incoming swap handle the storing of the htlc. We can't
 * do that without a serious refactor, so we add this model to avoid the issue entirely.
 */
data class IncomingSwapHtlcDb (
        val swapHoustonUuid: String,
        val htlc: IncomingSwapHtlc,
): HoustonUuidModel(null, htlc.houstonUuid) {

    override var id: Long?
        get() = htlc.id
        set(value) {
            htlc.id = value
        }

}