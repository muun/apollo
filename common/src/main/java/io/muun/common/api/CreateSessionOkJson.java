package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateSessionOkJson {

    @NotNull
    public Boolean isExistingUser;

    @NotNull
    public Boolean canUseRecoveryCode;

    /**
     * Json constructor.
     */
    public CreateSessionOkJson() {
    }

    /**
     * Manual constructor.
     */
    public CreateSessionOkJson(Boolean isExistingUser,
                               Boolean canUseRecoveryCode) {

        this.isExistingUser = isExistingUser;
        this.canUseRecoveryCode = canUseRecoveryCode;
    }
}
