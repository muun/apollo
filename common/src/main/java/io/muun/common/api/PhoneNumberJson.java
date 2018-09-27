package io.muun.common.api;

import io.muun.common.validator.E164PhoneNumber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhoneNumberJson {

    @NotEmpty
    @E164PhoneNumber
    public String number;

    @NotNull
    public Boolean isVerified;

    /**
     * Json constructor.
     */
    public PhoneNumberJson() {
    }

    /**
     * Constructor.
     */
    public PhoneNumberJson(String number, Boolean isVerified) {
        this.number = number;
        this.isVerified = isVerified;
    }
}
