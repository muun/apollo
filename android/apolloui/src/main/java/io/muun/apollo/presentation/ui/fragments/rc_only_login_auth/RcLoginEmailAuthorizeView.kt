package io.muun.apollo.presentation.ui.fragments.rc_only_login_auth

import io.muun.apollo.presentation.ui.base.SingleFragmentView

interface RcLoginEmailAuthorizeView : SingleFragmentView {

    fun setObfuscatedEmail(obfuscatedEmail: String)

    fun handleInvalidLinkError()

    fun handleExpiredLinkError()
}