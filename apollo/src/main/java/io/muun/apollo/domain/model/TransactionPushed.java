package io.muun.apollo.domain.model;


import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class TransactionPushed {

    @Nullable
    public final String hex;

    @NotNull
    public final NextTransactionSize nextTransactionSize;

    @NotNull
    public final OperationWithMetadata operation;

    /**
     * Constructor.
     */
    public TransactionPushed(@Nullable String hex,
                             NextTransactionSize nextTransactionSize,
                             OperationWithMetadata operation) {
        this.hex = hex;
        this.nextTransactionSize = nextTransactionSize;
        this.operation = operation;
    }
}
