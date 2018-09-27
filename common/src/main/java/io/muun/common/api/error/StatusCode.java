package io.muun.common.api.error;

import io.muun.common.net.HttpStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StatusCode {

    SUCCESS(HttpStatus.OK),
    CLIENT_FAILURE(HttpStatus.BAD_REQUEST),
    SERVER_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;

    StatusCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static boolean isErrorCode(int code) {

        return isClientErrorCode(code) || isServerErrorCode(code);
    }

    public static boolean isClientErrorCode(int code) {

        return code == CLIENT_FAILURE.getCode();
    }

    public static boolean isServerErrorCode(int code) {

        return code == SERVER_FAILURE.getCode();
    }

    @JsonCreator
    public static StatusCode fromValue(int value) {

        for (StatusCode status: StatusCode.values()) {
            if (status.code == value) {
                return status;
            }
        }

        throw new IllegalArgumentException("Invalid status code: " + value);
    }

    @JsonValue
    public int toValue() {
        return this.code;
    }
}
