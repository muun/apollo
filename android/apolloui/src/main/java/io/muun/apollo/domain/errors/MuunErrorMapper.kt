package io.muun.apollo.domain.errors

import io.muun.apollo.domain.errors.newop.AmountTooSmallError
import io.muun.apollo.domain.errors.newop.ExchangeRateWindowTooOldError
import io.muun.apollo.domain.errors.newop.InsufficientFundsError
import io.muun.apollo.domain.errors.newop.InvalidAddressError
import io.muun.apollo.domain.errors.p2p.CountryNotSupportedError
import io.muun.apollo.domain.errors.p2p.ExpiredVerificationCodeError
import io.muun.apollo.domain.errors.p2p.InvalidPhoneNumberError
import io.muun.apollo.domain.errors.p2p.InvalidVerificationCodeError
import io.muun.apollo.domain.errors.p2p.PhoneNumberAlreadyUsedError
import io.muun.apollo.domain.errors.p2p.RevokedVerificationCodeError
import io.muun.apollo.domain.errors.p2p.TooManyWrongVerificationCodesError
import io.muun.apollo.domain.errors.passwd.EmailAlreadyUsedError
import io.muun.apollo.domain.errors.passwd.EmailNotRegisteredError
import io.muun.apollo.domain.errors.passwd.IncorrectPasswordError
import io.muun.apollo.domain.errors.rc.CredentialsDontMatchError
import io.muun.apollo.domain.errors.rc.InvalidRecoveryCodeV2Error
import io.muun.apollo.domain.errors.rc.StaleChallengeKeyError
import io.muun.common.api.error.ErrorCode

object MuunErrorMapper {

    private val newMuunErrorByErrorCode: Map<ErrorCode, () -> MuunError> = mapOf(
        ErrorCode.DEPRECATED_CLIENT_VERSION to { DeprecatedClientVersionError() },
        ErrorCode.EXPIRED_SESSION to { ExpiredSessionError() },
        ErrorCode.INVALID_PHONE_NUMBER to { InvalidPhoneNumberError() },
        ErrorCode.COUNTRY_NOT_SUPPORTED to { CountryNotSupportedError() },
        ErrorCode.INVALID_VERIFICATION_CODE to { InvalidVerificationCodeError() },
        ErrorCode.REVOKED_VERIFICATION_CODE to { RevokedVerificationCodeError() },
        ErrorCode.EXPIRED_VERIFICATION_CODE to { ExpiredVerificationCodeError() },
        ErrorCode.TOO_MANY_WRONG_VERIFICATION_CODES to { TooManyWrongVerificationCodesError() }, // that's a mouthful :)
        ErrorCode.PHONE_NUMBER_ALREADY_USED to { PhoneNumberAlreadyUsedError() },
        ErrorCode.EMAIL_ALREADY_USED to { EmailAlreadyUsedError() },
        ErrorCode.EMAIL_NOT_REGISTERED to { EmailNotRegisteredError() },
        ErrorCode.INSUFFICIENT_CLIENT_FUNDS to { InsufficientFundsError() },
        ErrorCode.INVALID_ADDRESS to { InvalidAddressError() },
        ErrorCode.INVALID_PASSWORD to { IncorrectPasswordError() },
        ErrorCode.INVALID_CHALLENGE_SIGNATURE to { InvalidChallengeSignatureError() },
        ErrorCode.AMOUNT_SMALLER_THAN_DUST to { AmountTooSmallError(-1) }, // symbolic, this shouldn't happen
        ErrorCode.EXCHANGE_RATE_WINDOW_TOO_OLD to { ExchangeRateWindowTooOldError() },
        ErrorCode.EMAIL_LINK_EXPIRED to { ExpiredActionLinkError() },
        ErrorCode.EMAIL_LINK_INVALID to { InvalidActionLinkError() },
        ErrorCode.RECOVERY_CODE_V2_NOT_SET_UP to { InvalidRecoveryCodeV2Error() },
        ErrorCode.HTTP_TOO_MANY_REQUESTS to { TooManyRequestsError() },
        ErrorCode.STALE_CHALLENGE_KEY to { StaleChallengeKeyError() },
        ErrorCode.CREDENTIALS_DONT_MATCH to { CredentialsDontMatchError() },
    )

    private val errorCodeByNumericCode: Map<Long, ErrorCode> =
        ErrorCode.values().associateBy { it.code.toLong() }

    /**
     * Returns a MuunError given a houston error code,
     * or null if no mapping exists.
     */
    @JvmStatic
    fun map(numericCode: Long): MuunError? =
        errorCodeByNumericCode[numericCode]?.let { errorCode ->
            return newMuunErrorByErrorCode[errorCode]?.invoke()
        }

}
