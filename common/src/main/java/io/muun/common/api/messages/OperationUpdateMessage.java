package io.muun.common.api.messages;

import io.muun.common.api.NextTransactionSizeJson;
import io.muun.common.api.SubmarineSwapJson;
import io.muun.common.api.Transaction;
import io.muun.common.model.OperationStatus;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperationUpdateMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "operations/update",
            SessionStatus.LOGGED_IN,
            MessageOrigin.HOUSTON
    );

    public Long id;

    public Long confirmations;

    @Nullable
    public String hash;

    public OperationStatus status;

    public NextTransactionSizeJson nextTransactionSize;

    @Nullable
    public Transaction transaction;

    @Nullable
    public SubmarineSwapJson swapDetails;

    @Override
    public String toLog() {
        return String.format("Update about tx '%s' with %s confirmations", hash, confirmations);
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
                                  @Nullable String hash,
                                  OperationStatus status,
                                  NextTransactionSizeJson nextTransactionSize,
                                  SubmarineSwapJson swapDetails,
                                  @Nullable Transaction transaction) {
        this.id = id;
        this.confirmations = confirmations;
        this.hash = hash;
        this.status = status;
        this.nextTransactionSize = nextTransactionSize;
        this.swapDetails = swapDetails;
        this.transaction = transaction;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
