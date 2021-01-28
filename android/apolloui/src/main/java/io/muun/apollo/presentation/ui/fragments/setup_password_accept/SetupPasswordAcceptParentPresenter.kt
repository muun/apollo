package io.muun.apollo.presentation.ui.fragments.setup_password_accept

import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.presentation.ui.base.ParentPresenter
import rx.Observable

interface SetupPasswordAcceptParentPresenter: ParentPresenter {

    fun acceptPasswordSetupTerms()

    fun watchAcceptPasswordSetupTerms(): Observable<ActionState<Void>>

    fun cancelAcceptTerms()

}