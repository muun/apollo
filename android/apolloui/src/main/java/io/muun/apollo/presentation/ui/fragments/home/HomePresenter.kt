package io.muun.apollo.presentation.ui.fragments.home

import android.os.Bundle
import icepick.State
import io.muun.apollo.domain.action.user.UpdateUserPreferencesAction
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector
import io.muun.apollo.domain.selector.LatestOperationSelector
import io.muun.apollo.domain.selector.PaymentContextSelector
import io.muun.apollo.domain.selector.UserPreferencesSelector
import io.muun.apollo.domain.selector.UtxoSetStateSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import rx.Observable
import javax.inject.Inject


@PerFragment
class HomePresenter @Inject constructor(
        private val paymentContextSel: PaymentContextSelector,
        private val currencyDisplayModeSel: CurrencyDisplayModeSelector,
        private val userPreferencesSelector: UserPreferencesSelector,
        private val updateUserPreferencesAction: UpdateUserPreferencesAction,
        private val latestOperationSelector: LatestOperationSelector,
        private val utxoSetStateSelector: UtxoSetStateSelector
) : SingleFragmentPresenter<HomeView, HomeParentPresenter>() {

    @State
    @JvmField
    var lastOpId: Long? = null

    class HomeBalanceState(
            val paymentContext: PaymentContext,
            val currencyDisplayMode: CurrencyDisplayMode,
            val utxoSetState: UtxoSetStateSelector.UtxoSetState,
            val balanceHidden: Boolean
    )

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        Observable.combineLatest(
            paymentContextSel.watch(),
            currencyDisplayModeSel.watch(),
            utxoSetStateSelector.watch(),
            userSel.watchBalanceHidden(),
            ::HomeBalanceState)
            .compose(getAsyncExecutor())
            .doOnNext { homeBalance ->
                view.setBalance(homeBalance)
            }
            .let(this::subscribeTo)

        latestOperationSelector.watch()
            .compose(getAsyncExecutor())
            .doOnNext { maybeOp ->

                if (!maybeOp.isPresent) {
                    // Seed an imposible op id so the first operation ever is shown
                    lastOpId = -1
                }

                maybeOp.ifPresent { latestOp ->

                    if (lastOpId != null && lastOpId != latestOp.hid) {
                        view.setNewOp(latestOp, currencyDisplayModeSel.get())
                    }

                    lastOpId = latestOp.hid
                }
            }
            .let(this::subscribeTo)

        userSel.watch()
            .compose(getAsyncExecutor())
            .doOnNext { user ->
                view.setUserRecoverable(user.isRecoverable)
            }
            .let(this::subscribeTo)

        subscribeTo(userPreferencesSelector.watch()) { prefs ->
            if (!prefs.seenNewHome) {
                view.showTooltip()

                updateUserPreferencesAction.run {
                    it.copy(seenNewHome = true)
                }
            }
        }
    }


    fun navigateToReceiveScreen() {
        navigator.navigateToShowQr(context, AnalyticsEvent.RECEIVE_ORIGIN.RECEIVE_BUTTON)
    }

    fun navigateToSendScreen() {
        navigator.navigateToSend(context)
    }

    fun navigateToSecurityCenter() {
        parentPresenter.navigateToSecurityCenter()
    }

    fun setBalanceHidden(hidden: Boolean) {
        userSel.setBalanceHidden(hidden)
    }

    fun navigateToOperations() {
        parentPresenter.navigateToOperations()
    }
}