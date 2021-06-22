package io.muun.apollo.presentation.ui.fragments.settings

import android.net.Uri
import android.os.Bundle
import io.muun.apollo.domain.NightModeManager
import kotlin.Triple
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.action.user.UpdateProfilePictureAction
import io.muun.apollo.domain.errors.MuunError
import io.muun.apollo.domain.model.*
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector
import io.muun.apollo.domain.selector.ExchangeRateSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.analytics.AnalyticsEvent.*
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import io.muun.apollo.presentation.ui.settings.lightning.LightningSettingsFragment
import rx.Observable
import timber.log.Timber
import javax.inject.Inject
import javax.money.CurrencyUnit
import javax.validation.constraints.NotNull

@PerFragment
class SettingsPresenter @Inject constructor(
    private val currencyDisplayModeSel: CurrencyDisplayModeSelector,
    private val updateProfilePictureAction: UpdateProfilePictureAction,
    private val userActions: UserActions,
    private val exchangeRateSelector: ExchangeRateSelector,
    private val nightModeManager: NightModeManager

) : SingleFragmentPresenter<SettingsView, ParentPresenter>() {

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
                currencyDisplayModeSel.watch(),
                exchangeRateSelector.watchWindow(),
                ::Triple
            )
            .doOnNext { data: Triple<User, CurrencyDisplayMode, ExchangeRateWindow> ->
                val (user, mode, rateWindow) = data
                view.setUser(user, mode, rateWindow)
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
}
