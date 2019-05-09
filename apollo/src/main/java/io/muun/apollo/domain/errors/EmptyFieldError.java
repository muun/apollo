package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class EmptyFieldError extends UserFacingError {

    public enum Field {
        FIRST_NAME,
        LAST_NAME,
        PASSWORD
    }

    public EmptyFieldError(Field field) {
        super(UserFacingErrorMessages.INSTANCE.emptyField(field));
    }
}
