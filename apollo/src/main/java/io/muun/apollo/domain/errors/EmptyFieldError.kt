package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class EmptyFieldError(field: Field):
    UserFacingError(UserFacingErrorMessages.INSTANCE.emptyField(field)) {

    enum class Field {
        FIRST_NAME,
        LAST_NAME,
        PASSWORD
    }
}
