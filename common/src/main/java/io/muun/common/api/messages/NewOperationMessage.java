package io.muun.common.api.messages;

import io.muun.common.api.NextTransactionSizeJson;
import io.muun.common.api.OperationJson;
import io.muun.common.api.Transaction;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewOperationMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "operation/new",
            SessionStatus.LOGGED_IN,
            MessageOrigin.HOUSTON
    );


    public OperationJson operation;
    public NextTransactionSizeJson nextTransactionSize;

    @Override
    public String toLog() {
        final Transaction tx = operation.transaction;
        return String.format(
                "New tx '%s' with %s confirmations",
                tx.hash,
                tx.confirmations
        );
    }

    /**
     * Json constructor.
     */
    public NewOperationMessage() {
    }

    /**
     * Houston constructor.
     */
    public NewOperationMessage(OperationJson operation, NextTransactionSizeJson nextTxSize) {
        this.operation = operation;
        this.nextTransactionSize = nextTxSize;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
