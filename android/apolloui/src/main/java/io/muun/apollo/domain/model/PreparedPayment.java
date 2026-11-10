package io.muun.apollo.domain.model;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class PreparedPayment {

    @NotNull
    public final BitcoinAmount fee;

    @NotNull
    public final BitcoinAmount amount;

    @NotNull
    public final String description;

    @NotNull
    public final Long rateWindowHid;

    public final List<String> outpoints;

    public final PaymentRequest.Type type;

    public final Contact contact;

    public final String address;

    public final SubmarineSwap swap;

    public final long expectedDebtInSat;

    /**
     * Manual constructor.
     */
    public PreparedPayment(
            final BitcoinAmount amount,
            final BitcoinAmount fee,
            final String description,
            final Long rateWindowHid,
            final List<String> outpoints,
            final PaymentRequest.Type type,
            final Contact contact,
            final String address,
            final SubmarineSwap swap,
            final long expectedDebtInSat) {

        this.amount = amount;
        this.fee = fee;
        this.description = description;
        this.rateWindowHid = rateWindowHid;
        this.outpoints = outpoints;
        this.type = type;
        this.contact = contact;
        this.address = address;
        this.swap = swap;
        this.expectedDebtInSat = expectedDebtInSat;
    }
}
