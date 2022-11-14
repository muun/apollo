package io.muun.apollo.presentation.ui.fragments.sync

import io.muun.apollo.presentation.ui.base.SingleFragmentView

interface SyncView : SingleFragmentView {

    override fun setLoading(isLoading: Boolean)

    fun setIsExistingUser(isExistingUser: Boolean)

    fun setUpPinCode(canCancel: Boolean)
}