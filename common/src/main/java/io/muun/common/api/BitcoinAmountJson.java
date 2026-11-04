package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import javax.money.MonetaryAmount;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitcoinAmountJson {

    @NotNull
    public Long inSatoshis;

    @NotNull
    public MonetaryAmount inInputCurrency;

    @NotNull
    public MonetaryAmount inPrimaryCurrency;

    /**
     * Json constructor.
     */
    public BitcoinAmountJson() {
    }

    /**
     * Constructor.
     */
    public BitcoinAmountJson(Long inSatoshis,
                             MonetaryAmount inInputCurrency,
                             MonetaryAmount inPrimaryCurrency) {

        this.inSatoshis = inSatoshis;
        this.inInputCurrency = inInputCurrency;
        this.inPrimaryCurrency = inPrimaryCurrency;
    }
}
