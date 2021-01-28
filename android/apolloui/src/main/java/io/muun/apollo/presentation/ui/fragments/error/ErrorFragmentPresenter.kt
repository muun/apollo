package io.muun.apollo.presentation.ui.fragments.error

import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import javax.inject.Inject

class ErrorFragmentPresenter @Inject constructor():
    SingleFragmentPresenter<BaseView, ParentPresenter>() {

    fun goHomeInDefeat() {
        navigator.navigateToHome(context)
        view.finishActivity()
    }
}