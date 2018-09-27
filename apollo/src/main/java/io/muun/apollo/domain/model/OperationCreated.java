package io.muun.apollo.domain.model;


import io.muun.common.crypto.tx.PartiallySignedTransaction;

import javax.validation.constraints.NotNull;

public class OperationCreated {

    @NotNull
    public Operation operation;

    @NotNull
    public PartiallySignedTransaction partiallySignedTransaction;

    @NotNull
    public NextTransactionSize nextTransactionSize;

    /**
     * Constructor.
     */
    public OperationCreated(Operation operation,
                            PartiallySignedTransaction partiallySignedTransaction,
                            NextTransactionSize nextTransactionSize) {
        this.operation = operation;
        this.partiallySignedTransaction = partiallySignedTransaction;
        this.nextTransactionSize = nextTransactionSize;
    }
}
