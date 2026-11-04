package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartiallySignedTransactionJson {

    @NotEmpty
    public String hexTransaction;

    @NotNull
    public List<MuunInputJson> inputs;

    /**
     * Json constructor.
     */
    public PartiallySignedTransactionJson() {
    }

    /**
     * Houston constructor.
     */
    public PartiallySignedTransactionJson(String hexTransaction,
                                          List<MuunInputJson> inputs) {

        this.hexTransaction = hexTransaction;
        this.inputs = inputs;
    }
}
