package io.muun.apollo.domain.model;

import javax.validation.constraints.NotNull;

public class PreparedPayment {

    @NotNull
    public final BitcoinAmount fee;

    @NotNull
    public final BitcoinAmount amount;

    @NotNull
    public final String description;

    @NotNull
    public final Long rateWindowHid;

    public final NextTransactionSize nextTransactionSize;

    public final PaymentRequest payReq;

    /**
     * Manual constructor.
     */
    public PreparedPayment(
            final BitcoinAmount amount,
            final BitcoinAmount fee,
            final String description,
            final Long rateWindowHid,
            final NextTransactionSize nextTransactionSize,
            final PaymentRequest payReq) {

        this.amount = amount;
        this.fee = fee;
        this.description = description;
        this.rateWindowHid = rateWindowHid;
        this.nextTransactionSize = nextTransactionSize;
        this.payReq = payReq;
    }


}
