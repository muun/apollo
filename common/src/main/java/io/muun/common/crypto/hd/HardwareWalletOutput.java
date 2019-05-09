package io.muun.common.crypto.hd;

import javax.validation.constraints.NotNull;

public class HardwareWalletOutput {

    @NotNull
    private final String txId;

    private final int index;

    private final Long amount;

    private final PublicKey publicKey;

    private String rawPreviousTransaction;

    /**
     * Constructor.
     */
    public HardwareWalletOutput(
            String txId,
            int index,
            long amount,
            PublicKey publicKey,
            String rawPreviousTransaction) {

        this.txId = txId;
        this.index = index;
        this.amount = amount;
        this.publicKey = publicKey;
        this.rawPreviousTransaction = rawPreviousTransaction;
    }

    public String getTxId() {
        return txId;
    }

    public int getIndex() {
        return index;
    }

    public Long getAmount() {
        return amount;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setRawPreviousTransaction(String rawPreviousTransaction) {
        this.rawPreviousTransaction = rawPreviousTransaction;
    }

    public String getRawPreviousTransaction() {
        return rawPreviousTransaction;
    }
}
