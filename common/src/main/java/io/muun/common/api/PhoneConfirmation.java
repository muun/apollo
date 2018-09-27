package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhoneConfirmation {

    @NotNull
    public String verificationCode;

    /**
     * Json constructor.
     */
    public PhoneConfirmation() {
    }

    /**
     * Apollo constructor.
     */
    public PhoneConfirmation(String verificationCode) {
        this.verificationCode = verificationCode;
    }
}
