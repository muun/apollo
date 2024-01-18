package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapRequestJson {

    @NotEmpty
    public String invoice;

    @NotNull
    public Integer swapExpirationInBlocks;

    @Nullable // For retrocompat endpoint
    public String origin;

    @Nullable // For retrocompat endpoint
    public List<BackgroundEventJson> bkgTimes;

    /**
     * Json constructor.
     */
    public SubmarineSwapRequestJson() {
    }

    /**
     * Manual constructor.
     */
    public SubmarineSwapRequestJson(
            String invoice,
            int swapExpirationInBlocks,
            @Nullable String origin,
            @Nullable List<BackgroundEventJson> bkgTimes
    ) {
        this.invoice = invoice;
        this.swapExpirationInBlocks = swapExpirationInBlocks;
        this.origin = origin;
        this.bkgTimes = bkgTimes;
    }
}
