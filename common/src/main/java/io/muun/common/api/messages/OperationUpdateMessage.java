package io.muun.common.api.messages;

import io.muun.common.api.NextTransactionSizeJson;
import io.muun.common.model.OperationStatus;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperationUpdateMessage extends AbstractMessage {

    public static final String TYPE = "operations/update";

    public static final SessionStatus PERMISSION = SessionStatus.LOGGED_IN;

    public Long id;

    public Long confirmations;

    @Nullable
    public String hash;

    public OperationStatus status;

    public NextTransactionSizeJson nextTransactionSize;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public SessionStatus getPermission() {
        return PERMISSION;
    }

    @Override
    public String toLog() {
        return String.format("%s about tx '%s' with %s confirmations", TYPE, hash, confirmations);
    }

    /**
     * Json constructor.
     */
    public OperationUpdateMessage() {
    }

    /**
     * Houston constructor.
     */
    public OperationUpdateMessage(Long id,
                                  Long confirmations,
                                  String hash,
                                  OperationStatus status,
                                  NextTransactionSizeJson nextTransactionSize) {
        this.id = id;
        this.confirmations = confirmations;
        this.hash = hash;
        this.status = status;
        this.nextTransactionSize = nextTransactionSize;
    }
}
