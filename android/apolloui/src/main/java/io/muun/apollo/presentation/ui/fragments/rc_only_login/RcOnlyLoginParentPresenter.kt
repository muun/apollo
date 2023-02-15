package io.muun.apollo.presentation.ui.fragments.rc_only_login

import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.domain.model.CreateSessionRcOk
import rx.Observable

interface RcOnlyLoginParentPresenter : ParentPresenter {

    fun watchLoginWithRcOnly(): Observable<ActionState<CreateSessionRcOk>>

    fun loginWithRcOnly(recoveryCode: String)

    fun cancelLoginWithRcOnly()
}
