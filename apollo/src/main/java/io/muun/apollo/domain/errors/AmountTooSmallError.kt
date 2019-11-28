package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class AmountTooSmallError(amountInSats: Long):
    UserFacingError(UserFacingErrorMessages.INSTANCE.amountTooSmall()) {

    init {
        metadata["amountInSats"] = amountInSats
    }

}
