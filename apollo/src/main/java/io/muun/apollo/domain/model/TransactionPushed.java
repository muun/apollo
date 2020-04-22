package io.muun.apollo.domain.model;


import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class TransactionPushed {

    @Nullable
    public final String hex;

    @NotNull
    public final NextTransactionSize nextTransactionSize;

    /**
     * Constructor.
     */
    public TransactionPushed(@Nullable String hex, NextTransactionSize nextTransactionSize) {
        this.hex = hex;
        this.nextTransactionSize = nextTransactionSize;
    }
}
