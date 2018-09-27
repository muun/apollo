package io.muun.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public enum OperationStatus {

    /**
     * Newly created operation, without an associated transaction, or with an unsigned one.
     */
    CREATED,

    /**
     * Operation stored both in the server an on the signing client, in the process of being
     * signed.
     */
    SIGNING,

    /**
     * Operation with an associated transaction, which has been signed but not broadcasted yet.
     */
    SIGNED,

    /**
     * Operation with a transaction that's already been broadcasted, but hasn't confirmed yet.
     */
    BROADCASTED,

    /**
     * Operation with its transaction present in a block (0 < confirmations < SETTLEMENT_NUMBER),
     * but not with enough transactions to be settled.
     */
    CONFIRMED,

    /**
     * Operation with its transaction settled (confirmations >= SETTLEMENT_NUMBER).
     */
    SETTLED,

    /**
     * Operation with a transaction that hasn't been present in the last blockchain sync.
     */
    DROPPED,

    /**
     * Operation's transaction was rejected by the network.
     */
    FAILED;

    @JsonCreator
    public static OperationStatus fromValue(String value) {
        return OperationStatus.valueOf(value.toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return this.name().toUpperCase();
    }
}
