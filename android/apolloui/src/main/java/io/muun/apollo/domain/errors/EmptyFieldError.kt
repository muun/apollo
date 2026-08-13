package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class EmptyFieldError(field: Field) : UserFacingError(
    UserFacingErrorMessages.INSTANCE.emptyField(field)
) {

    override val classification = ErrorClassification.EXPECTED

    enum class Field {
        FIRST_NAME,
        LAST_NAME,
        PASSWORD
    }
}
