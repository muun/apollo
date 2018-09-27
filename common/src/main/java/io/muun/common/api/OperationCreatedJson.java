package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationCreatedJson {

    @NotNull
    public OperationJson operation;

    @NotNull
    public PartiallySignedTransaction partiallySignedTransaction;

    @NotNull
    public NextTransactionSizeJson nextTransactionSize;

    /**
     * Json constructor.
     */
    public OperationCreatedJson() {
    }

    /**
     * Houston constructor.
     */
    public OperationCreatedJson(OperationJson operation,
                                PartiallySignedTransaction partiallySignedTransaction,
                                NextTransactionSizeJson nextTransactionSize) {

        this.operation = operation;
        this.partiallySignedTransaction = partiallySignedTransaction;
        this.nextTransactionSize = nextTransactionSize;
    }
}
