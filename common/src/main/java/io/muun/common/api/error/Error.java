package io.muun.common.api.error;

import io.muun.common.exception.HttpException;
import io.muun.common.utils.RandomGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Error {

    public Long requestId;

    public StatusCode status;

    public ErrorCode errorCode;

    public String message;

    public String developerMessage;

    public String clientSession;

    public Error() {
    }

    public Error(HttpException exception, String sessionUuid) {
        this.requestId = exception.getRequestId();
        this.status = exception.getErrorCode().getStatus();
        this.errorCode = exception.getErrorCode();
        this.message = exception.getMessage();
        this.developerMessage = exception.getDeveloperMessage();
        this.clientSession = sessionUuid;
    }

    public Error(ErrorCode errorCode) {
        this.requestId = RandomGenerator.getPositiveLong();
        this.status = errorCode.getStatus();
        this.errorCode = errorCode;
        this.message = errorCode.getDescription();
    }

    public Error(ErrorCode errorCode, String message) {
        this(errorCode);
        this.developerMessage = message;
    }
}
