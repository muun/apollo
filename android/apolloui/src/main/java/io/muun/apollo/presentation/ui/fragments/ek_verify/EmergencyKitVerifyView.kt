package io.muun.apollo.presentation.ui.fragments.ek_verify

import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.BaseView

interface EmergencyKitVerifyView: BaseView {
    fun setVerificationError(error: UserFacingError)
}