package io.muun.apollo.presentation.ui.fragments.enter_email

import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.BaseView

interface CreateEmailView: BaseView {

    fun setLoading(isLoading: Boolean)

    fun setEmail(email: String?)

    fun setEmailError(error: UserFacingError?)

}