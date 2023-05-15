package io.muun.apollo.presentation.ui.fragments.tr_success

import android.os.Bundle
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.ACTIVE
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.PREACTIVATED
import io.muun.apollo.domain.selector.BlockchainHeightSelector
import io.muun.apollo.domain.selector.UserActivatedFeatureStatusSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent.FEEDBACK_TYPE
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_FEEDBACK
import io.muun.apollo.presentation.ui.base.Presenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.fragments.single_action.SingleActionPresenter
import libwallet.Libwallet
import rx.Observable
import javax.inject.Inject

@PerFragment
class TaprootSuccessPresenter @Inject constructor(
    private val blockchainHeightSel: BlockchainHeightSelector,
    private val userActivatedFeatureStatusSel: UserActivatedFeatureStatusSelector
): SingleActionPresenter<TaprootSuccessView, Presenter<*>>() {

    override fun setUp(arguments: Bundle?) {
        super.setUp(arguments)

        Observable
            .combineLatest(
                blockchainHeightSel.watchBlocksToTaproot(),
                userActivatedFeatureStatusSel.watch(Libwallet.getUserActivatedFeatureTaproot()),
                this::combineState
            )
            .first() // a ton of indirect state dependencies, they just complicate things. Bye.
            .doOnNext {
                reportEventForStatus(it.taprootStatus)
                view.setState(it)
            }
            .let(this::subscribeTo)
    }

    private fun combineState(blocksToTaproot: Int, featureStatus: UserActivatedFeatureStatus) =
        TaprootSuccessView.State(
            blocksToTaproot,
            BlockchainHeightSelector.getBlocksInHours(blocksToTaproot),
            featureStatus
        )

    private fun reportEventForStatus(taprootStatus: UserActivatedFeatureStatus) {
        val feedbackEventType = when (taprootStatus) {
            ACTIVE -> FEEDBACK_TYPE.TAPROOT_ACTIVATION_SUCCESS
            PREACTIVATED -> FEEDBACK_TYPE.TAPROOT_PREACTIVATION_SUCCESS
            else ->
                throw IllegalStateException("Status can't be ${taprootStatus}!")
        }

        analytics.report(S_FEEDBACK(feedbackEventType))
    }
}