package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrityStatus {

    @NotNull
    public Boolean isBasePublicKeyOk;

    @NotNull
    public Boolean isExternalMaxUsedIndexOk;

    @NotNull
    public Boolean isBalanceOk;

    /**
     * JSON constructor.
     */
    public IntegrityStatus() {
    }

    public boolean isOk() {
        // Oh man, this indentation D: why so serious, Linter?
        return (
                isBalanceOk
                        && isBasePublicKeyOk
                        && isExternalMaxUsedIndexOk);
    }
}
