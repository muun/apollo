package io.muun.apollo.domain.model;

import io.muun.apollo.domain.analytics.NewOperationOrigin;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SubmarineSwapRequest {

    @NotNull
    public String invoice;

    @NotNull
    public Integer swapExpirationInBlocks;

    @NotNull
    public NewOperationOrigin origin;

    @NotNull
    public List<BackgroundEvent> bkgTimes;

    /**
     * Json constructor.
     */
    public SubmarineSwapRequest() {
    }

    /**
     * Manual constructor.
     */
    public SubmarineSwapRequest(
            String invoice,
            int swapExpirationInBlocks,
            NewOperationOrigin origin,
            List<BackgroundEvent> bkgTimes
    ) {
        this.invoice = invoice;
        this.swapExpirationInBlocks = swapExpirationInBlocks;
        this.origin = origin;
        this.bkgTimes = bkgTimes;
    }
}

