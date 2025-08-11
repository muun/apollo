package io.muun.apollo.presentation.ui.fragments.settings

import android.net.Uri
import android.os.Bundle
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.domain.NightModeManager
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.action.session.LogoutAction
import io.muun.apollo.domain.action.user.DeleteWalletAction
import io.muun.apollo.domain.action.user.UpdateProfilePictureAction
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_SETTINGS
import io.muun.apollo.domain.analytics.AnalyticsEvent.WalletDeleteState
import io.muun.apollo.domain.errors.delete_wallet.NonEmptyWalletDeleteException
import io.muun.apollo.domain.errors.delete_wallet.UnsettledOperationsWalletDeleteException
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.model.user.UserProfile
import io.muun.apollo.domain.selector.BitcoinUnitSelector
import io.muun.apollo.domain.selector.ExchangeRateSelector
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.apollo.domain.selector.UserActivatedFeatureStatusSelector
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.settings.bitcoin.BitcoinSettingsFragment
import io.muun.apollo.presentation.ui.settings.lightning.LightningSettingsFragment
import io.muun.common.Optional
import io.muun.common.api.messages.EventCommunicationMessage.Event
import io.muun.common.utils.Preconditions
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.money.CurrencyUnit

@PerFragment
class SettingsPresenter @Inject constructor(
    private val bitcoinUnitSel: BitcoinUnitSelector,
    private val updateProfilePictureAction: UpdateProfilePictureAction,
    private val logoutAction: LogoutAction,
    private val deleteWallet: DeleteWalletAction,
    private val userActions: UserActions,
    private val exchangeRateSelector: ExchangeRateSelector,
    private val userActivatedFeatureStatusSel: UserActivatedFeatureStatusSelector,
    private val nightModeManager: NightModeManager,
    private val notificationService: NotificationService,
    private val featureSelector: FeatureSelector,
) : SingleFragmentPresenter<SettingsView, ParentPresenter>() {

    class SettingsState(
        val user: User,
        val bitcoinUnit: BitcoinUnit,
        val exchangeRateWindow: ExchangeRateWindow,
        val taprootFeatureStatus: UserActivatedFeatureStatus,
        val features: List<MuunFeature>,
    )

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)
        setUpUserWatcher()
        setUpUpdateProfilePictureAction()
        setUpUpdatePrimaryCurrencyAction()
        setUpDeleteWalletAction()
        setUpNightMode()
    }

    private fun setUpUserWatcher() {
        val observable: Observable<*> = Observable
            .combineLatest(
                userSel.watch(),
                bitcoinUnitSel.watch(),
                exchangeRateSelector.watchLatestWindow(),
                userActivatedFeatureStatusSel.watchTaproot(),
                featureSelector.fetch(),
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

    private fun setUpDeleteWalletAction() {
        val observable = deleteWallet
            .state
            .compose(handleStates(view::setLoading, this::handleWalletDeleteError))
            .doOnNext { maybeSupportId: Optional<String> ->
                onWalletDeleted(maybeSupportId)
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
    fun logoutUser() {
        analytics.report(AnalyticsEvent.E_LOG_OUT())
        analytics.resetUserProperties()

        logoutAction.run()
        navigator.navigateToLauncher(context)

        // We need to finish this activity, or the session status check will immediately raise
        // the SessionExpired error -- even though this was a regular logout.
        view.finishActivity()
    }

    /**
     * Call to delete wallet.
     */
    fun deleteWallet() {
        analytics.report(AnalyticsEvent.E_WALLET_DELETE(WalletDeleteState.STARTED))
        deleteWallet.run()
    }

    /**
     * Perform successful delete wallet follow up actions (e.g cleanup, navigation, etc...).
     */
    private fun onWalletDeleted(maybeSupportId: Optional<String>) {
        analytics.report(AnalyticsEvent.E_WALLET_DELETE(WalletDeleteState.SUCCESS))
        analytics.resetUserProperties()

        navigator.navigateToDeleteWallet(context, maybeSupportId)

        // We need to finish this activity, or the session status check will immediately raise
        // the SessionExpired error -- even though this is expected from deleteWallet.
        view.finishActivity()
    }

    /**
     * Handle the tap on the log out button.
     */
    fun handleLogoutRequest() {

        if (!userSel.getOptional().isPresent) {
            Timber.e("This shouldn't happen. Already logged out user")
            return
        }

        // TODO: this should not be using a blocking observable. Not terrible, not ideal.
        val options = logoutOptionsSel.get()
        val shouldBlockAndExplain = options.isLogoutBlocked()

        Preconditions.checkArgument(options.isRecoverable())
        view.handleLogout(shouldBlockAndExplain)
    }

    /**
     * Handle the tap on the delete wallet button.
     */
    fun handleDeleteWalletRequest() {

        if (!userSel.getOptional().isPresent) {
            Timber.e("This shouldn't happen. Already logged out user")
            return
        }

        // TODO: this should not be using a blocking observable. Not terrible, not ideal.
        val options = logoutOptionsSel.get()

        view.handleDeleteWallet(options.canDeleteWallet(), options.isRecoverable())
    }

    override fun getEntryEvent(): AnalyticsEvent {
        return S_SETTINGS()
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

    private fun handleWalletDeleteError(error: Throwable?) {
        analytics.report(AnalyticsEvent.E_WALLET_DELETE(WalletDeleteState.ERROR))
        when (error) {
            is NonEmptyWalletDeleteException -> view.showCantDeleteNonEmptyWalletDialog()
            is UnsettledOperationsWalletDeleteException -> view.showCantDeleteNonEmptyWalletDialog()
            else -> super.handleError(error)
        }
    }
}
