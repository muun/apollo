package io.muun.apollo.presentation.ui.fragments.home

import android.os.Bundle
import icepick.State
import io.muun.apollo.domain.action.user.UpdateUserPreferencesAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.model.SecurityCenter
import io.muun.apollo.domain.model.SecurityLevel
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.domain.selector.BlockchainHeightSelector
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.apollo.domain.selector.LatestOperationSelector
import io.muun.apollo.domain.selector.OperationSelector
import io.muun.apollo.domain.selector.PaymentContextSelector
import io.muun.apollo.domain.selector.UserActivatedFeatureStatusSelector
import io.muun.apollo.domain.selector.UserPreferencesSelector
import io.muun.apollo.domain.selector.UtxoSetStateSelector
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.fragments.operations.OperationsCache
import io.muun.apollo.presentation.ui.fragments.tr_clock_detail.TaprootClockDetailFragment
import libwallet.Libwallet
import rx.Observable
import javax.inject.Inject


@PerFragment
class HomeFragmentPresenter @Inject constructor(
    private val paymentContextSel: PaymentContextSelector,
    private val bitcoinUnitSel: BitcoinUnitSelector,
    private val userPreferencesSel: UserPreferencesSelector,
    private val updateUserPreferencesAction: UpdateUserPreferencesAction,
    private val operationSelector: OperationSelector,
    private val latestOperationSelector: LatestOperationSelector,
    private val utxoSetStateSelector: UtxoSetStateSelector,
    private val userActivatedFeatureStatusSel: UserActivatedFeatureStatusSelector,
    private val featureSelector: FeatureSelector,
    private val blockchainHeightSel: BlockchainHeightSelector,
    private val operationsCache: OperationsCache,
) : SingleFragmentPresenter<HomeFragmentView, HomeFragmentParentPresenter>() {

    @State
    @JvmField
    var lastOpId: Long? = null

    class HomeState(
        val paymentContext: PaymentContext,
        val bitcoinUnit: BitcoinUnit,
        val utxoSetState: UtxoSetStateSelector.UtxoSetState,
        val balanceHidden: Boolean,
        val user: User,
        val taprootFeatureStatus: UserActivatedFeatureStatus,
        val blocksToTaproot: Int,
        val highFees: Boolean,
    )

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        operationsCache.watch()
            .doOnNext {
                val securityCenter = SecurityCenter(it.user, userPreferencesSel.emailSetupSkipped())
                reportHome(securityCenter, it.operations.isNotEmpty())
            }
            .let(this::subscribeTo)

        Observable
            .combineLatest(
                paymentContextSel.watch(),
                bitcoinUnitSel.watch(),
                utxoSetStateSelector.watch(),
                userSel.watchBalanceHidden(),
                userSel.watch(),
                userActivatedFeatureStatusSel.watch(Libwallet.getUserActivatedFeatureTaproot()),
                blockchainHeightSel.watchBlocksToTaproot(),
                featureSelector.fetch(MuunFeature.HIGH_FEES_HOME_BANNER),
                ::HomeState
            )
            .compose(getAsyncExecutor())
            .doOnNext(view::setState)
            .let(this::subscribeTo)

        val newOpId = HomeFragmentArgs.fromBundle(view.argumentsBundle).newOpId
        if (newOpId > 0) {
            // Then we are coming from recently submitting an outgoing op. The best way to ALWAYS
            // show newop badge in this case is this (newop deeplink + process death needs this)

            val newOperation = operationSelector.fetchByHId(newOpId)
            view.setNewOp(newOperation, bitcoinUnitSel.get())

            lastOpId = newOpId
        }

        latestOperationSelector.watch()
            .compose(getAsyncExecutor())
            .doOnNext { maybeOp ->

                if (!maybeOp.isPresent) {
                    // Seed an impossible op id so the first operation ever is shown
                    lastOpId = -1
                }

                maybeOp.ifPresent { latestOp ->

                    if (lastOpId != null && lastOpId != latestOp.hid) {
                        view.setNewOp(latestOp, bitcoinUnitSel.get())
                    }

                    lastOpId = latestOp.hid
                }
            }
            .let(this::subscribeTo)

        subscribeTo(userPreferencesSel.watch()) { prefs ->
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

    fun navigateToTaprootSetup() {
        navigator.navigateToTaprootSetup(context)
    }

    fun navigateToSendScreen() {
        navigator.navigateToSend(context)
    }

    fun navigateToSecurityCenter() {
        parentPresenter.navigateToSecurityCenter()
    }

    fun navigateToHighFeesExplanationScreen() {
        parentPresenter.navigateToHighFeesExplanationScreen()
    }

    fun setBalanceHidden(hidden: Boolean) {
        analytics.report(AnalyticsEvent.E_BALANCE_TAP())
        userSel.setBalanceHidden(hidden)
    }

    fun navigateToOperations() {
        parentPresenter.navigateToOperations()
    }

    fun navigateToClockDetail() {
        navigator.navigateToFragment(context, TaprootClockDetailFragment::class.java)
    }

    private fun reportHome(securityCenter: SecurityCenter, hashOperations: Boolean) {

        val securityLevel: SecurityLevel = securityCenter.getLevel()
        val isAnonUser = (securityLevel === SecurityLevel.ANON
            || securityLevel === SecurityLevel.SKIPPED_EMAIL_ANON)

        analytics.report(AnalyticsEvent.S_HOME(getHomeType(isAnonUser, hashOperations)))
    }

    private fun getHomeType(
        isAnonUser: Boolean,
        hasOperations: Boolean,
    ): AnalyticsEvent.S_HOME_TYPE {

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