package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MuunOutputJson {

    @NotNull
    public String txId;

    @NotNull
    public Integer index;

    @NotNull
    public Long amount;

    /**
     * Json constructor.
     */
    public MuunOutputJson() {
    }

    /**
     * Manual constructor.
     */
    public MuunOutputJson(String txId, Integer index, Long amount) {

        this.txId = txId;
        this.index = index;
        this.amount = amount;
    }
}
