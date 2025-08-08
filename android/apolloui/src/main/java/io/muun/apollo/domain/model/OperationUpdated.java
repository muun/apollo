package io.muun.apollo.domain.model;

import io.muun.common.model.OperationStatus;

import androidx.annotation.Nullable;

import javax.validation.constraints.NotNull;

public class OperationUpdated {

    @NotNull
    public final Long hid;

    @NotNull
    public final Long confirmations;

    // Null for certain kinds of lightning operations
    @Nullable
    public final String hash;

    @NotNull
    public final OperationStatus status;

    @NotNull
    public final NextTransactionSize nextTransactionSize;

    @Nullable
    public final SubmarineSwap submarineSwap;

    /**
     * Constructor.
     */
    public OperationUpdated(Long hid,
                            Long confirmations,
                            @Nullable String hash,
                            OperationStatus status,
                            NextTransactionSize nextTransactionSize,
                            @Nullable SubmarineSwap submarineSwap) {

        this.hid = hid;
        this.confirmations = confirmations;
        this.hash = hash;
        this.status = status;
        this.nextTransactionSize = nextTransactionSize;
        this.submarineSwap = submarineSwap;
    }
}
