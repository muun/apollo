package io.muun.apollo.domain.model;

import javax.validation.constraints.NotNull;

public class SubmarineSwapRequest {

    @NotNull
    public String invoice;

    @NotNull
    public Integer swapExpirationInBlocks;

    /**
     * Json constructor.
     */
    public SubmarineSwapRequest() {
    }

    /**
     * Manual constructor.
     */
    public SubmarineSwapRequest(String invoice, int swapExpirationInBlocks) {
        this.invoice = invoice;
        this.swapExpirationInBlocks = swapExpirationInBlocks;
    }
}

