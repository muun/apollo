package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

import javax.validation.constraints.NotNull;

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
