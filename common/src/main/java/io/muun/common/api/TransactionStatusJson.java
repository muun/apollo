package io.muun.common.api;

public enum TransactionStatusJson {

    /** Newly created transactions that hasn't been broadcasted yet. */
    CREATED,

    /** Transaction has already been broadcasted, but hasn't confirmed yet. */
    BROADCASTED,

    /** Transaction present in a block, but not with enough confirmations to be settled. */
    CONFIRMED,

    /** Transaction settled (confirmations >= SETTLEMENT_NUMBER). */
    SETTLED,

    /** Transaction hasn't been present in the last blockchain sync. */
    DROPPED,

    /** Transaction was rejected by the network. */
    FAILED,

    /** Transaction was prepared but has not been sent yet. */
    PREPARED
}
