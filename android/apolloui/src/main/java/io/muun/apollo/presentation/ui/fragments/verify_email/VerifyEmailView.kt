package io.muun.apollo.presentation.ui.fragments.verify_email

import io.muun.apollo.presentation.ui.base.SingleFragmentView

interface VerifyEmailView : SingleFragmentView {

    fun setEmail(email: String)

    fun handleInvalidLinkError()

    fun handleExpiredLinkError()
}