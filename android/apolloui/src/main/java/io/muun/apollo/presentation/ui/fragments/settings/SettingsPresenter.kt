package io.muun.apollo.presentation.ui.fragments.settings

import android.net.Uri
import android.os.Bundle
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.domain.NightModeManager
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.action.user.UpdateProfilePictureAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_LOG_OUT
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_WALLET_DELETED
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_SETTINGS
import io.muun.apollo.domain.errors.MuunError
import io.muun.apollo.domain.libwallet.UAF_TAPROOT
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.model.user.UserProfile
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.domain.selector.ExchangeRateSelector
import io.muun.apollo.domain.selector.UserActivatedFeatureStatusSelector
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.settings.bitcoin.BitcoinSettingsFragment
import io.muun.apollo.presentation.ui.settings.lightning.LightningSettingsFragment
import io.muun.common.api.messages.EventCommunicationMessage.Event
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.money.CurrencyUnit

@PerFragment
class SettingsPresenter @Inject constructor(
    private val bitcoinUnitSel: BitcoinUnitSelector,
    private val updateProfilePictureAction: UpdateProfilePictureAction,
    private val userActions: UserActions,
    private val exchangeRateSelector: ExchangeRateSelector,
    private val userActivatedFeatureStatusSel: UserActivatedFeatureStatusSelector,
    private val nightModeManager: NightModeManager,
    private val notificationService: NotificationService,
) : SingleFragmentPresenter<SettingsView, ParentPresenter>() {

    class SettingsState(
        val user: User,
        val bitcoinUnit: BitcoinUnit,
        val exchangeRateWindow: ExchangeRateWindow,
        val taprootFeatureStatus: UserActivatedFeatureStatus,
    )

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        setUpUserWatcher()
        setUpUpdateProfilePictureAction()
        setUpUpdatePrimaryCurrencyAction()
        setUpNightMode()
    }

    private fun setUpUserWatcher() {
        val observable: Observable<*> = Observable
            .combineLatest(
                userSel.watch(),
                bitcoinUnitSel.watch(),
                exchangeRateSelector.watchLatestWindow(),
                userActivatedFeatureStatusSel.watch(UAF_TAPROOT),
                ::SettingsState
            )
            .doOnNext { state ->
                view.setState(state)
            }

        subscribeTo(observable)
    }

    private fun setUpUpdateProfilePictureAction() {
        if (!userSel.get().profile.isPresent) {
            view.hidePublicProfileSection()
            return
        }
        val observable: Observable<*> = updateProfilePictureAction.state
            .doOnNext { state: ActionState<UserProfile?> ->
                when (state.kind) {
                    ActionState.Kind.VALUE -> view.profilePictureUpdated(state.value)
                    ActionState.Kind.ERROR -> {
                        view.setLoading(false)
                        handleError(state.error)
                    }
                    else -> {
                    }
                }
            }
        subscribeTo(observable)
    }

    private fun setUpUpdatePrimaryCurrencyAction() {
        val observable = userActions.updatePrimaryCurrencyAction
            .state
            .doOnNext { state: ActionState<User?> ->
                when (state.kind) {
                    ActionState.Kind.VALUE -> {
                    }
                    ActionState.Kind.ERROR -> handleError(state.error)
                    else -> {
                    }
                }
            }
        subscribeTo(observable)
    }

    private fun setUpNightMode() {
        view.setNightMode(nightModeManager.get())
    }

    fun navigateToEditUsername() {
        navigator.navigateToEditUsername(context)
    }

    fun navigateToEditPassword() {
        navigator.navigateToEditPassword(context)
    }

    fun navigateToSelectBitcoinUnit() {
        navigator.navigateToSelectBitcoinUnit(context)
    }

    fun navigateToSelectDarkMode() {
        navigator.navigateToSelectDarkMode(context)
    }

    /**
     * Call to report the profile picture has changed.
     */
    fun reportPictureChange(pictureUri: Uri?) {
        userActions.setPendingProfilePicture(pictureUri)
        updateProfilePictureAction.run()
    }

    fun onPrimaryCurrencyChanged(currencyUnit: CurrencyUnit?) {
        userActions.updatePrimaryCurrencyAction.run(currencyUnit)
    }

    /**
     * Call to logout.
     */
    fun logout() {
        analytics.report(E_LOG_OUT())
        analytics.resetUserProperties()

        // We need to "capture" auth header to fire (and forget) notifyLogout request
        val jwt = getJwt()
        navigator.navigateToLogout(context)

        // We need to finish this activity, or the session status check will immediately raise
        // the SessionExpired error -- even though this was a regular logout.
        view.finishActivity()
        userActions.notifyLogoutAction.run(jwt)
    }

    /**
     * Call to delete wallet.
     */
    fun deleteWallet() {
        analytics.report(E_WALLET_DELETED())
        analytics.resetUserProperties()

        // We need to "capture" auth header to fire (and forget) notifyLogout request
        val jwt = getJwt()
        navigator.navigateToDeleteWallet(context)

        // We need to finish this activity, or the session status check will immediately raise
        // the SessionExpired error -- even though this was a regular logout.
        view.finishActivity()
        userActions.notifyLogoutAction.run(jwt)
    }

    /**
     * Handle the tap on the delete wallet or log out buttons.
     */
    fun handleDeleteWalletRequest() {
        // TODO: this should not be using a blocking observable. Not terrible, not ideal.
        val options = logoutOptionsSel.watch()
            .toBlocking()
            .first()
        val shouldBlockAndExplain = options.isBlocked()
        if (options.isRecoverable()) {
            view.handleLogout(shouldBlockAndExplain)

        } else {
            view.handleDeleteWallet(shouldBlockAndExplain)
        }
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return S_SETTINGS()
    }

    private fun getJwt(): String {
        val serverJwt = authRepository.serverJwt
        if (!serverJwt.isPresent) {
            // Shouldn't happen but isn't the worst if it does. We wanna know 'cause probably a bug
            Timber.e(MuunError("Auth token expected to be present"))
        }
        return serverJwt.get()
    }

    fun navigateToLightningSettings() {
        navigator.navigateToFragment(context, LightningSettingsFragment::class.java)
    }

    fun navigateToBitcoinSettings() {
        navigator.navigateToFragment(context, BitcoinSettingsFragment::class.java)
    }

    fun showPreactivationNotification() {
        notificationService.showEventCommunication(Event.TAPROOT_PREACTIVATION)
    }

    fun showActivatedNotification() {
        notificationService.showEventCommunication(Event.TAPROOT_ACTIVATED)
    }

    fun openDebugPanel() {
        navigator.navigateToDebugPanel(context)
    }
}
