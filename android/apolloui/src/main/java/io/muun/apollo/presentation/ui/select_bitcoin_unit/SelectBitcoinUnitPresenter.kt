package io.muun.apollo.presentation.ui.select_bitcoin_unit

import android.os.Bundle
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.model.BitcoinUnit
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
        setUpBitcoinUnit()
    }

    private fun setUpBitcoinUnit() {
        val observable = userRepository
            .watchBitcoinUnit()
            .doOnNext(view::setBitcoinUnit)

        subscribeTo(observable)
    }

    fun changeBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        userRepository.bitcoinUnit = bitcoinUnit
        analytics.report(AnalyticsEvent.E_DID_SELECT_BITCOIN_UNIT(bitcoinUnit))
        view.finishActivity()
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_BITCOIN_UNIT_PICKER()
    }
}