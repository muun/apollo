package io.muun.apollo.presentation.ui.fragments.operations

import android.os.Bundle
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class OperationsPresenter @Inject constructor(
    private val operationsCache: OperationsCache,
) : SingleFragmentPresenter<OperationsView, ParentPresenter>() {

    class ReactiveState<T>(
        val operations: List<T>,
        val bitcoinUnit: BitcoinUnit,
        val user: User,
    )

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        setUpViewState()
    }

    private fun setUpViewState() {
        val observable = operationsCache.watch()
            .doOnNext {
                view.setViewState(it.operations)
            }

        subscribeTo(observable)
    }

    fun onOperationClicked(operationId: Long?) {
        navigator.navigateToOperationDetail(context, operationId)
    }

    fun goToReceive() {
        navigator.navigateToShowQr(context, AnalyticsEvent.RECEIVE_ORIGIN.TX_EMPTY_STATE)
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_TRANSACTIONS()
    }
}