package io.muun.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public enum VerificationType {

    /**
     * Verification code is sent via SMS.
     */
    SMS,

    /**
     * Verification code is delivered via a phone call.
     */
    CALL;

    public static VerificationType DEFAULT = SMS;

    @JsonCreator
    public static VerificationType fromValue(String value) {
        return VerificationType.valueOf(value.toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return this.name().toUpperCase();
    }
}
