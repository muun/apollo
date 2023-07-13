package io.muun.apollo.presentation.ui.fragments.tr_clock_detail

import android.os.Bundle
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_EASTER_EGG
import io.muun.apollo.domain.analytics.AnalyticsEvent.FEEDBACK_TYPE
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_FEEDBACK
import io.muun.apollo.domain.selector.BlockchainHeightSelector
import io.muun.apollo.presentation.ui.base.Presenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.fragments.single_action.SingleActionPresenter
import javax.inject.Inject

@PerFragment
class TaprootClockDetailPresenter @Inject constructor(
    private val blockchainHeightSel: BlockchainHeightSelector
): SingleActionPresenter<TaprootClockDetailView, Presenter<*>>() {

    override fun setUp(arguments: Bundle?) {
        super.setUp(arguments)

        blockchainHeightSel.watchBlocksToTaproot()
            .doOnNext {
                view.setTaprootCounter(it)
            }
            .let(this::subscribeTo)
    }

    override fun getEntryEvent() =
        S_FEEDBACK(FEEDBACK_TYPE.TAPROOT_PREACTIVATION_COUNTDOWN)

    fun reportEasterEgg(launchedApp: String) {
        analytics.report(E_EASTER_EGG("TheFinalCountDown-$launchedApp"))
    }
}