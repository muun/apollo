package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class AmountTooSmallError(amountInSats: Long):
    UserFacingError(UserFacingErrorMessages.INSTANCE.amountTooSmall()) {

    init {
        metadata["amountInSats"] = amountInSats
    }

}
