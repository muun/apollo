package io.muun.common.exception;

import io.muun.common.api.error.BaseErrorCode;
import io.muun.common.api.error.Error;
import io.muun.common.utils.RandomGenerator;

import javax.annotation.Nullable;

public class HttpException extends RuntimeException {

    private final Long requestId;

    private final BaseErrorCode errorCode;

    @Nullable
    private final String developerMessage;

    private static long getRandomId() {
        return RandomGenerator.getPositiveLong();
    }

    /**
     * Constructor.
     */
    public HttpException(Long requestId, BaseErrorCode errorCode, @Nullable String message) {

        super(errorCode.getDescription());
        this.requestId = requestId;
        this.errorCode = errorCode;
        this.developerMessage = message;
    }

    /**
     * Constructor.
     */
    public HttpException(
            Long requestId,
            BaseErrorCode errorCode,
            @Nullable String message,
            Throwable cause) {

        super(errorCode.getDescription(), cause);
        this.requestId = requestId;
        this.errorCode = errorCode;
        this.developerMessage = message;
    }

    public HttpException(BaseErrorCode errorCode) {
        this(getRandomId(), errorCode, errorCode.getDescription());
    }

    public HttpException(BaseErrorCode errorCode, String message) {
        this(getRandomId(), errorCode, message);
    }

    public HttpException(BaseErrorCode errorCode, Throwable cause) {
        this(getRandomId(), errorCode, cause.getMessage(), cause);
    }

    public HttpException(BaseErrorCode errorCode, String message, Throwable cause) {
        this(getRandomId(), errorCode, message, cause);
    }

    public HttpException(Error error) {
        this(error.requestId, error.errorCode, error.developerMessage);
    }

    public Long getRequestId() {
        return requestId;
    }

    public BaseErrorCode getErrorCode() {
        return errorCode;
    }

    @Nullable
    public String getDeveloperMessage() {
        return developerMessage;
    }
}
