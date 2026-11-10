package io.muun.apollo.domain.model;


import io.muun.apollo.domain.model.feebump.FeeBumpFunctions;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

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
