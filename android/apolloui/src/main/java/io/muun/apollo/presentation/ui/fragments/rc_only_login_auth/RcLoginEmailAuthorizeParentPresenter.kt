package io.muun.apollo.presentation.ui.fragments.rc_only_login_auth

import io.muun.apollo.presentation.ui.base.ParentPresenter

interface RcLoginEmailAuthorizeParentPresenter : ParentPresenter {

    fun getOfuscatedEmail(): String

    fun cancelRcLoginEmailAuth()

    fun reportRcLoginEmailVerified()
}
