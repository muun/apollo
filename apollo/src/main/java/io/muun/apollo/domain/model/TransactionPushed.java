package io.muun.apollo.domain.model;


import javax.validation.constraints.NotNull;

public class TransactionPushed {

    @NotNull
    public final String hex;

    @NotNull
    public final NextTransactionSize nextTransactionSize;

    /**
     * Constructor.
     */
    public TransactionPushed(String hex, NextTransactionSize nextTransactionSize) {
        this.hex = hex;
        this.nextTransactionSize = nextTransactionSize;
    }
}
