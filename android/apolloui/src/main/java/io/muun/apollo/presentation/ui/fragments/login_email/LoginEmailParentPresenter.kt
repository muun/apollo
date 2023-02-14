package io.muun.apollo.presentation.ui.fragments.login_email

import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.model.SignupDraft
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.domain.model.CreateSessionOk
import rx.Observable

interface LoginEmailParentPresenter: ParentPresenter {

    fun useRecoveryCodeOnlyLogin()

    fun submitEmail(email: String)

    fun watchSubmitEmail(): Observable<ActionState<CreateSessionOk>>

    fun cancelEnterEmail()

    val signupDraft: SignupDraft
}