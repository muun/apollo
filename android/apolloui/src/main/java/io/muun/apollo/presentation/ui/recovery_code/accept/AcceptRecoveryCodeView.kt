package io.muun.apollo.presentation.ui.recovery_code.accept

import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.ui.base.SingleFragmentView

interface AcceptRecoveryCodeView : SingleFragmentView {

    /**
     * Set this view's texts based on the current state of the user.
     */
    fun setTexts(user: User)

    /**
     * Set this view's state to loading.
     */
    override fun setLoading(isLoading: Boolean)
}