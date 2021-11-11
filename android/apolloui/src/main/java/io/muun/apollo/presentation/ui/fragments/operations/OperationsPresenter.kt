package io.muun.apollo.presentation.ui.fragments.operations

import android.os.Bundle
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.SecurityCenter
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject
import javax.validation.constraints.NotNull

@PerFragment
class OperationsPresenter @Inject constructor(
    private val operationsCache: OperationsCache
) : SingleFragmentPresenter<OperationsView, ParentPresenter>() {

    class ReactiveState<T>(
        val operations: List<T>,
        val currencyDisplayMode: CurrencyDisplayMode,
        val user: User
    )

    override fun setUp(@NotNull arguments: Bundle) {
        super.setUp(arguments)
        setUpViewState()
    }

    private fun setUpViewState() {
        val observable = operationsCache.watch()
            .doOnNext {
                val securityCenter = SecurityCenter(it.user, userSel.emailSetupSkipped())
                view.setViewState(it.operations, securityCenter)
            }

        subscribeTo(observable)
    }

    fun onOperationClicked(operationId: Long?) {
        navigator.navigateToOperationDetail(context, operationId)
    }

    fun goToReceive() {
        navigator.navigateToShowQr(context, AnalyticsEvent.RECEIVE_ORIGIN.TX_EMPTY_STATE)
    }

    fun reportHome(isAnonUser: Boolean, hashOperations: Boolean) {
        analytics.report(AnalyticsEvent.S_HOME(getHomeType(isAnonUser, hashOperations)))
    }

    private fun getHomeType(isAnonUser: Boolean, hasOperations: Boolean): AnalyticsEvent.S_HOME_TYPE {
        return if (isAnonUser) {
            if (hasOperations) {
                AnalyticsEvent.S_HOME_TYPE.ANON_USER_WITH_OPERATIONS
            } else {
                AnalyticsEvent.S_HOME_TYPE.ANON_USER_WITHOUT_OPERATIONS
            }
        } else {
            if (hasOperations) {
                AnalyticsEvent.S_HOME_TYPE.USER_SET_UP_WITH_OPERATIONS
            } else {
                AnalyticsEvent.S_HOME_TYPE.USER_SET_UP_WITHOUT_OPERATIONS
            }
        }
    }
}