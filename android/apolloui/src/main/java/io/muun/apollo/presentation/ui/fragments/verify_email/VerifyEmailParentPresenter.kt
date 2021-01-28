package io.muun.apollo.presentation.ui.fragments.verify_email

import io.muun.apollo.presentation.ui.base.ParentPresenter

interface VerifyEmailParentPresenter: ParentPresenter {

    fun getEmail(): String?

    fun cancelVerifyEmail()

}