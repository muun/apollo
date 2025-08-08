package io.muun.apollo.domain.model

import io.muun.apollo.data.external.Globals

/**
 * We need empty constructors to work with jackson de/serialization.
 * On the JVM, if the generated class needs to have a parameterless constructor, default values
 * for all properties have to be specified. See:
 * https://kotlinlang.org/docs/reference/classes.html#constructors
 */
class SignupDraft(
    var versionCode: Int = Globals.INSTANCE.versionCode,
    var isExistingUser: Boolean = false,
    var canUseRecoveryCode: Boolean = false,
    var step: SignupStep = SignupStep.START,
    var previousStep: SignupStep? = null,
    var obfuscatedEmail: String? = null,
    var email: String? = null,
    var loginWithRc: LoginWithRc? = null,
    var showUnverifiedRcWarning: Boolean = false
)

class LoginWithRc(val rc: String = "", var keysetFetchNeeded: Boolean = false)
