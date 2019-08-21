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

    /**
     * Manual constructor.
     */
    public PreparedPayment(
            BitcoinAmount amount,
            BitcoinAmount fee,
            String description,
            Long rateWindowHid) {

        this.amount = amount;
        this.fee = fee;
        this.description = description;
        this.rateWindowHid = rateWindowHid;
    }


}
