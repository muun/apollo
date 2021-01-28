package io.muun.apollo.presentation.ui.fragments.login_email

import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.BaseView


interface LoginEmailView: BaseView {
    fun setLoading(isLoading: Boolean)

    fun setEmailError(error: UserFacingError?)

    fun autoFillEmail(email: String)
}