package io.muun.apollo.domain.model;


import io.muun.common.crypto.tx.PartiallySignedTransaction;

import javax.validation.constraints.NotNull;

public class OperationCreated {

    @NotNull
    public final Operation operation;

    @NotNull
    public final PartiallySignedTransaction partiallySignedTransaction;

    @NotNull
    public final NextTransactionSize nextTransactionSize;

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
