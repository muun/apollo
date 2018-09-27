package io.muun.apollo.domain.errors;


public class EmptyFieldError extends UserFacingError {

    public EmptyFieldError(String fieldName) {
        super(fieldName + " is required");
    }
}
