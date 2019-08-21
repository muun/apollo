package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapRequestJson {

    @NotEmpty
    public String invoice;

    @NotNull
    public Integer swapExpirationInBlocks;

    /**
     * Json constructor.
     */
    public SubmarineSwapRequestJson() {
    }

    /**
     * Manual constructor.
     */
    public SubmarineSwapRequestJson(String invoice, int swapExpirationInBlocks) {
        this.invoice = invoice;
        this.swapExpirationInBlocks = swapExpirationInBlocks;
    }
}
