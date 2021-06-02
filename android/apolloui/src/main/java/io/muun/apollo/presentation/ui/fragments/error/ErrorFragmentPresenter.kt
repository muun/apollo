package io.muun.apollo.presentation.ui.fragments.error

import android.os.Bundle
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import javax.inject.Inject

class ErrorFragmentPresenter @Inject constructor():
    SingleFragmentPresenter<ErrorView, ParentPresenter>() {

    private lateinit var viewModel: ErrorViewModel

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        viewModel = getErrorViewModel(arguments)
        view.setViewModel(viewModel)
    }

    private fun getErrorViewModel(arguments: Bundle): ErrorViewModel =
        ErrorViewModel.deserialize(arguments.getString(ErrorFragment.VIEW_MODEL_ARG)!!)

    fun goHomeInDefeat() {
        navigator.navigateToHome(context)
        view.finishActivity()
    }

    override fun getEntryEvent(): AnalyticsEvent =
        AnalyticsEvent.E_ERROR(viewModel.loggingName())
}