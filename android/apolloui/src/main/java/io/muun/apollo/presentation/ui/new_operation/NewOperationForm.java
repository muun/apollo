package io.muun.apollo.presentation.ui.new_operation;

import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;

import org.javamoney.moneta.Money;

import javax.annotation.Nullable;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;


public class NewOperationForm {

    @NotNull
    final OperationUri operationUri;

    @Nullable // during resolve
    public PaymentRequest payReq;

    @Nullable // Set once the user presses submit with the definite pay req used
    public PaymentRequest submitedPayReq;

    @NotNull
    public MonetaryAmount amount;
    public boolean isAmountFixed;
    public boolean isAmountConfirmed;
    public boolean isUsingAllFunds;

    @NotNull
    public String description;
    public boolean isDescriptionConfirmed;

    public Double selectedFeeRate;
    public boolean isFeeFixed;

    public boolean displayInAlternateCurrency;

    /**
     * Constructor with defaults.
     */
    public NewOperationForm(OperationUri operationUri) {
        this.amount = Money.of(0, "BTC");
        this.description = "";
        this.operationUri = operationUri;
    }
}
