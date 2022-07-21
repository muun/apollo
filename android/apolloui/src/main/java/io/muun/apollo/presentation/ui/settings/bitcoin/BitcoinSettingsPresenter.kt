package io.muun.apollo.presentation.ui.settings.bitcoin

import android.os.Bundle
import io.muun.apollo.domain.action.user.UpdateUserPreferencesAction
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.domain.selector.BlockchainHeightSelector
import io.muun.apollo.domain.selector.FeatureStatusSelector
import io.muun.apollo.domain.selector.UserPreferencesSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import libwallet.Libwallet
import rx.Observable
import javax.inject.Inject

class BitcoinSettingsPresenter @Inject constructor(
    private val userPreferencesSel: UserPreferencesSelector,
    private val blockchainHeightSel: BlockchainHeightSelector,
    private val featureStatusSel: FeatureStatusSelector,
    private val updateUserPreferences: UpdateUserPreferencesAction
): SingleFragmentPresenter<BitcoinSettingsView, ParentPresenter>() {

    class State(
        val taprootByDefault: Boolean,
        val blocksToTaproot: Int,
        val taprootStatus: UserActivatedFeatureStatus
    )

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        val combined = Observable.combineLatest(
            userPreferencesSel.watch().map { userPref -> userPref.defaultAddressType == "taproot" },
            blockchainHeightSel.watchBlocksToTaproot(),
            featureStatusSel.watch(Libwallet.getUserActivatedFeatureTaproot()),
            ::State
        )

        subscribeTo(combined) { state ->
            onStateChange(state)
        }

        // Handle loading and errors. Action updates userPrefs repo, triggers a change in selector
        updateUserPreferences.state
            .compose(handleStates(view::setLoading, this::handleError))
            .let(this::subscribeTo)
    }

    private fun onStateChange(state: State) {
        view.setTaprootByDefault(state.taprootByDefault)
        view.setTaprootStatus(
            state.taprootStatus,
            BlockchainHeightSelector.getBlocksInHours(state.blocksToTaproot)
        )
    }

    fun reportTaprootByDefaultChange(taprootByDefault: Boolean) {
        val defaultAddressType = if (taprootByDefault) "taproot" else "segwit"
        updateUserPreferences.run { prefs ->
            prefs.copy(defaultAddressType = defaultAddressType)
        }
    }

    override fun getEntryEvent() =
        AnalyticsEvent.S_SETTINGS_BITCOIN_NETWORK()
}