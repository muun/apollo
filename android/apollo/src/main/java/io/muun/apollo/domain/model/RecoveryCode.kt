package io.muun.apollo.domain.model

import io.muun.apollo.domain.libwallet.RecoveryCodeV2
import io.muun.apollo.domain.libwallet.errors.UnknownRecoveryCodeVersionError
import libwallet.Libwallet


class RecoveryCode {

    companion object {
        @JvmStatic
        fun validate(code: String) {

            when (getRecoveryCodeVersionOrDefault(code)) {

                1 -> RecoveryCodeV1.fromString(code)
                2 -> RecoveryCodeV2.fromString(code)

                else -> throw UnknownRecoveryCodeVersionError(RuntimeException())
            }
        }

        /**
         * Workaround for Libwallet not handling partial recovery codes/validations. We default
         * to V2 so we can do some partial validations.
         */
        fun getRecoveryCodeVersionOrDefault(recoveryCode: String): Int {
            return try {
                getRecoveryCodeVersion(recoveryCode)
            } catch (e: Exception) {
                return 2
            }
        }

        /**
         * Get the version for the recovery code given, by looking at its format.
         * If no version can be recognized, it returns an error.
         */
        private fun getRecoveryCodeVersion(recoveryCode: String): Int {
            return try {
                Libwallet.getRecoveryCodeVersion(recoveryCode).toInt()
            } catch (e: Exception) {
                throw UnknownRecoveryCodeVersionError(e)
            }
        }
    }

    abstract class RecoveryCodeError : RuntimeException() {
    }

    class RecoveryCodeLengthError : RecoveryCodeError()

    class RecoveryCodeAlphabetError : RecoveryCodeError()
}