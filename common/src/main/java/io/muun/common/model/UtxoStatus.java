package io.muun.common.model;

import io.muun.common.api.UtxoStatusJson;
import io.muun.common.exception.MissingCaseError;

public enum UtxoStatus {

    UNCONFIRMED,

    CONFIRMED;

    /**
     * Parse from a json-serializable representation.
     */
    public static UtxoStatus fromJson(UtxoStatusJson statusJson) {

        switch (statusJson) {

            case UNCONFIRMED:
                return UNCONFIRMED;

            case CONFIRMED:
                return CONFIRMED;

            default:
                throw new MissingCaseError(statusJson);
        }
    }

    /**
     * Convert to a json-serializable representation.
     */
    public UtxoStatusJson toJson() {

        switch (this) {

            case UNCONFIRMED:
                return UtxoStatusJson.UNCONFIRMED;

            case CONFIRMED:
                return UtxoStatusJson.CONFIRMED;

            default:
                throw new MissingCaseError(this);
        }
    }
}
