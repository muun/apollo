package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignedWithdrawalOperationJson {

    @NotNull
    public OperationJson operation;

    @NotNull
    public String signedTransaction;

    /**
     * Json constructor.
     */
    public SignedWithdrawalOperationJson() {
    }

    /**
     * Manual constructor.
     */
    public SignedWithdrawalOperationJson(OperationJson operation, String signedTransaction) {
        this.operation = operation;
        this.signedTransaction = signedTransaction;
    }
}
