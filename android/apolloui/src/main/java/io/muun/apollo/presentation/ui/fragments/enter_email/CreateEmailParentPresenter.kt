package io.muun.apollo.presentation.ui.fragments.enter_email

import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.presentation.ui.base.ParentPresenter
import rx.Observable

interface CreateEmailParentPresenter : ParentPresenter {

    fun refreshToolbarTitle()

    fun submitEmail(email: String)

    fun watchSubmitEmail(): Observable<ActionState<Void>>

    fun cancelCreateEmail()

    fun getEmail(): String?
}