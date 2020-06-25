package io.muun.apollo.domain.model

import io.muun.apollo.external.Globals

class SignupDraft(
    var versionCode: Int = Globals.INSTANCE.versionCode,
    var isExistingUser: Boolean = false,
    var canUseRecoveryCode: Boolean = false,
    var step: SignupStep = SignupStep.START,
    var email: String? = null
)
