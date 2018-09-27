package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {

    @Nullable
    public String hash;

    public Long confirmations;

    /**
     * Json constructor.
     */
    public Transaction() {
    }

    /**
     * Houston constructor.
     */
    public Transaction(@Nullable String hash, Long confirmations) {
        this.hash = hash;
        this.confirmations = confirmations;
    }
}
