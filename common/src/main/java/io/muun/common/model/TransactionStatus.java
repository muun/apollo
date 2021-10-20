package io.muun.common.model;

import io.muun.common.api.TransactionStatusJson;
import io.muun.common.bitcoinj.NetworkParametersHelper;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;

public enum TransactionStatus {

    /**
     * Newly created transactions that hasn't been broadcasted yet.
     */
    CREATED,

    /**
     * Transaction has already been broadcasted, but hasn't confirmed yet.
     */
    BROADCASTED,

    /**
     * Transaction present in a block (0 < confirmations < SETTLEMENT_NUMBER), but not with enough
     * confirmations to be settled.
     */
    CONFIRMED,

    /**
     * Transaction settled (confirmations >= SETTLEMENT_NUMBER).
     */
    SETTLED,

    /**
     * Transaction hasn't been present in the last blockchain sync.
     */
    DROPPED,

    /**
     * Transaction was rejected by the network.
     */
    FAILED;

    /**
     * Get the transactions status for a certain number of confirmations.
     */
    public static TransactionStatus getStatusForConfirmations(
            int confirmations,
            NetworkParameters network) {

        Preconditions.checkNotNegative(confirmations);

        if (confirmations == 0) {
            return TransactionStatus.BROADCASTED;
        }

        if (confirmations < NetworkParametersHelper.getSettlementNumber(network)) {
            return TransactionStatus.CONFIRMED;
        }

        return TransactionStatus.SETTLED;
    }

    /**
     * Parse from a json-serializable representation.
     */
    public static TransactionStatus fromJson(TransactionStatusJson status) {

        switch (status) {

            case CREATED:
                return CREATED;

            case BROADCASTED:
                return BROADCASTED;

            case CONFIRMED:
                return CONFIRMED;

            case SETTLED:
                return SETTLED;

            case DROPPED:
                return DROPPED;

            case FAILED:
                return FAILED;

            default:
                throw new MissingCaseError(status);
        }
    }

    /**
     * Whether the transaction has been broadcasted, but doesn't have enough confirmations to be
     * considered settled.
     */
    public boolean isUnsettled() {

        return this == BROADCASTED || this == CONFIRMED;
    }

    /**
     * Convert to a json-serializable representation.
     */
    public TransactionStatusJson toJson() {

        switch (this) {

            case CREATED:
                return TransactionStatusJson.CREATED;

            case BROADCASTED:
                return TransactionStatusJson.BROADCASTED;

            case CONFIRMED:
                return TransactionStatusJson.CONFIRMED;

            case SETTLED:
                return TransactionStatusJson.SETTLED;

            case DROPPED:
                return TransactionStatusJson.DROPPED;

            case FAILED:
                return TransactionStatusJson.FAILED;

            default:
                throw new MissingCaseError(this);
        }
    }
}
