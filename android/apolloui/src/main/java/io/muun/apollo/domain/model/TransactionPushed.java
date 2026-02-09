package io.muun.apollo.domain.model;


import io.muun.apollo.domain.model.feebump.FeeBumpFunctions;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class TransactionPushed {

    @Nullable
    public final String hex;

    @NotNull
    public final NextTransactionSize nextTransactionSize;

    @NotNull
    public final OperationWithMetadata operation;

    @NotNull
    public final FeeBumpFunctions feeBumpFunctions;

    /**
     * Constructor.
     */
    public TransactionPushed(@Nullable String hex,
                             NextTransactionSize nextTransactionSize,
                             OperationWithMetadata operation,
                             FeeBumpFunctions feeBumpFunctions) {
        this.hex = hex;
        this.nextTransactionSize = nextTransactionSize;
        this.operation = operation;
        this.feeBumpFunctions = feeBumpFunctions;
    }
}
