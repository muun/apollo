package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HardwareWalletOutputJson {

    @NotNull
    public String txId;

    @NotNull
    public Integer index;

    @NotNull
    public Long amount;

    @NotNull
    public PublicKeyJson publicKeyJson;

    @NotNull
    public String rawPreviousTransaction;

    /**
     * Json constructor.
     */
    public HardwareWalletOutputJson() {
    }

    /**
     * Manual constructor.
     */
    public HardwareWalletOutputJson(
            String txId,
            Integer index,
            Long amount,
            PublicKeyJson publicKeyJson,
            String rawPreviousTransaction) {

        this.txId = txId;
        this.index = index;
        this.amount = amount;
        this.publicKeyJson = publicKeyJson;
        this.rawPreviousTransaction = rawPreviousTransaction;
    }
}
