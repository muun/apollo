package io.muun.apollo.domain.model;


import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

public class BitcoinUriContent {
    @NotNull
    public final String address;

    @Nullable
    public final Long amountInStatoshis;

    @Nullable
    public final String memo;

    @Nullable
    public final String merchant;

    /**
     * Create a BitcoinUriContent with no amount, memo or merchant.
     */
    public BitcoinUriContent(String address) {
        this(address, null, null, null);
    }

    /**
     * Create a BitcoinUriContent.
     */
    public BitcoinUriContent(String address, Long amountInStatoshis, String memo, String merchant) {
        this.address = address;
        this.amountInStatoshis = amountInStatoshis;
        this.memo = memo;
        this.merchant = merchant;
    }
}
