package io.muun.common.crypto.hd;

import javax.validation.constraints.NotNull;

public class MuunOutput {

    @NotNull
    private final String txId;

    private final int index;

    private final long amount;

    /**
     * Constructor.
     */
    public MuunOutput(String txId, int index, long amount) {
        this.txId = txId;
        this.index = index;
        this.amount = amount;
    }

    public String getTxId() {
        return txId;
    }

    public int getIndex() {
        return index;
    }

    public long getAmount() {
        return amount;
    }
}
