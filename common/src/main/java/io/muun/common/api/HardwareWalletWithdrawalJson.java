package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HardwareWalletWithdrawalJson {

    @NotNull
    public OperationJson operation;

    @NotNull
    public RawTransaction signedTransaction;

    @NotNull
    public List<Long> inputAmounts;

    /**
     * Json constructor.
     */
    public HardwareWalletWithdrawalJson() {
    }

    /**
     * Apollo constructor.
     */
    public HardwareWalletWithdrawalJson(
            OperationJson operation,
            RawTransaction signedTransaction,
            List<Long> inputAmounts) {

        this.operation = operation;
        this.signedTransaction = signedTransaction;
        this.inputAmounts = inputAmounts;
    }
}
