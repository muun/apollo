package io.muun.apollo.domain.model;


import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.tx.PartiallySignedTransaction;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class OperationCreated {

    @NotNull
    public final Operation operation;

    @NotNull
    public final PartiallySignedTransaction partiallySignedTransaction;

    @NotNull
    public final NextTransactionSize nextTransactionSize;

    @Nullable // null if the Operation has no change
    public final MuunAddress changeAddress;

    /**
     * Constructor.
     */
    public OperationCreated(Operation operation,
                            PartiallySignedTransaction partiallySignedTransaction,
                            NextTransactionSize nextTransactionSize,
                            @Nullable MuunAddress changeAddress) {

        this.operation = operation;
        this.partiallySignedTransaction = partiallySignedTransaction;
        this.nextTransactionSize = nextTransactionSize;
        this.changeAddress = changeAddress;
    }
}
