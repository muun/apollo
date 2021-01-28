package io.muun.apollo.presentation.ui.select_bitcoin_unit

import android.os.Bundle
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import javax.inject.Inject
import javax.validation.constraints.NotNull

@PerActivity
class SelectBitcoinUnitPresenter @Inject constructor(
    private val userRepository: UserRepository

): BasePresenter<SelectBitcoinUnitView>() {

    override fun setUp(@NotNull arguments: Bundle) {
        super.setUp(arguments)
        setUpCurrencyDisplayMode()
    }

    private fun setUpCurrencyDisplayMode() {
        val observable = userRepository
            .watchCurrencyDisplayMode()
            .doOnNext(view::setCurrencyDisplayMode)

        subscribeTo(observable)
    }

    fun changeCurrencyDisplayMode(displayMode: CurrencyDisplayMode) {
        userRepository.currencyDisplayMode = displayMode
        analytics.report(AnalyticsEvent.E_DID_SELECT_BITCOIN_UNIT(displayMode))
        view.finishActivity()
    }

    override fun getEntryEvent(): AnalyticsEvent? {
        return AnalyticsEvent.S_BITCOIN_UNIT_PICKER()
    }
}