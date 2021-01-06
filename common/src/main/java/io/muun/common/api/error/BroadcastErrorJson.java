package io.muun.common.api.error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public enum BroadcastErrorJson {
    UNKNOWN_ERROR(
            0, "Unknown error"
    ),
    TOO_LONG_MEMPOOL_CHAIN(
            1,  "The pack associated to this transaction is too long"
    ),
    SPENDS_CONFLICTING_OUTPUTS(
            2, "Tx spends outputs that would be replaced by it"
    ),
    REPLACEMENT_LOW_FEE_RATE(
            3, "New feerate is lower than or equal to old feerate"
    ),
    REPLACEMENT_TOO_MANY_REPLACEMENTS(
            4, "Too many potential replacements"
    ),
    REPLACEMENTS_ADDS_UNCONFIRMED(
            5, "Replacements adds new mempool spends"
    ),
    REPLACEMENT_NOT_ENOUGH_FOR_RELAY(
            6, "Not enough additional fees to relay"
    ),
    REPLACEMENT_LESS_FEE_THAN_DESCENDANTS(
            7, "Less fees than conflicting txs"
    );

    private final int code;

    private final String description;

    BroadcastErrorJson(int code, String description) {

        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
