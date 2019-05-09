package io.muun.common.crypto.hd;

import io.muun.common.api.MuunOutputJson;

import javax.validation.constraints.NotNull;

public class MuunOutput {

    @NotNull
    private final String txId;

    private final int index;

    private final long amount;

    public static MuunOutput fromJson(MuunOutputJson json) {
        return new MuunOutput(json.txId, json.index, json.amount);
    }

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

    public MuunOutputJson toJson() {
        return new MuunOutputJson(txId, index, amount);
    }
}
