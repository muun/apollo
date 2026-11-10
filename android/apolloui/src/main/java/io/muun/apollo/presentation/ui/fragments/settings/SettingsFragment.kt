package io.muun.apollo.presentation.ui.fragments.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.data.external.Globals
import io.muun.apollo.databinding.FragmentSettingsBinding
import io.muun.apollo.domain.ClipboardManager
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.model.NightMode
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.ACTIVE
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.PREACTIVATED
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.SCHEDULED_ACTIVATION
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.model.user.UserProfile
import io.muun.apollo.presentation.biometrics.BiometricsController
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.fragments.settings.SettingsPresenter.SettingsState
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.select_currency.SelectCurrencyActivity
import io.muun.apollo.presentation.ui.utils.isInNightMode
import io.muun.apollo.presentation.ui.utils.supportsDarkMode
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.common.model.Currency
import javax.inject.Inject

open class SettingsFragment : SingleFragment<SettingsPresenter>(), SettingsView {

    private val REQUEST_NEW_PRIMARY_CURRENCY = 4

    @Inject
    lateinit var clipboardManager: ClipboardManager

    @Inject
    lateinit var biometricsController: BiometricsController

    /** Whether the loading dialog is currently on screen. Required due to limitations of
     * AlertDialogExtensions. It can't handle multiple dismissDialogs() calls prompted by
     * handleStates(). */
    private var showingLoadingDialog = false

    private val binding: FragmentSettingsBinding
        get() = getBinding() as FragmentSettingsBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding =
        FragmentSettingsBinding::inflate

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        // Tell parent activity we want a say in how, when and if menu items are/should be displayed
        setHasOptionsMenu(true)

        binding.settingsProfilePicture.setOnErrorListener { error: UserFacingError? -> presenter.handleError(error) }
        binding.settingsProfilePicture.setOnChangeListener { uri: Uri -> onPictureChange(uri) }
        binding.settingsVersionCode.text = getString(R.string.settings_version, getVersionForDisplay())

        binding.settingsEditUsername.setOnClickListener { editUsername() }
        binding.settingsPassword.setOnClickListener { editPassword() }
        binding.settingsDarkMode.setOnClickListener { editDarkMode() }
        binding.settingsBitcoinUnit.setOnClickListener { editBitcoinUnit() }
        binding.settingsLogout.setOnClickListener { logout() }
        binding.settingsDeleteWallet.setOnClickListener { deleteWallet() }
        binding.settingsBitcoin.setOnClickListener { goToBitcoinSettings() }
        binding.settingsLightning.setOnClickListener { goToLightningSettings() }
        binding.settingsDiagnostic.setOnClickListener { goToDiagnosticMode() }

        if (Globals.INSTANCE.isDebug) {
            binding.settingsVersionCode.setOnClickListener { presenter.openDebugPanel() }
        } else if (Globals.INSTANCE.isDogfood) {
            binding.settingsVersionCode.setOnClickListener {
                clipboardManager.copy("version", binding.settingsVersionCode.text.toString())
                showTextToast(getString(R.string.show_qr_copied))
            }
        }
    }

    override fun setUpHeader() {
        // For PublicProfile, if need be we'll show it later (yes, this default is backwards)
        parentActivity.header.visibility = View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear() // Effectively state that we don't want menu items. Yeap, this is how its done.
    }

    override fun hidePublicProfileSection() {
        parentActivity.header.apply {
            visibility = View.VISIBLE
            setNavigation(Navigation.NONE)
            showTitle(R.string.settings_title_profile_hidden)
            setElevated(true)
        }
        binding.settingsHeaderSection.visibility = View.GONE
    }

    override fun setState(state: SettingsState) {
        setUpRecoveryAndLogoutSection(state.user)
        setupSecuritySection()
        setUpPublicProfileSection(state.user)
        setUpWalletDetailsSection(state.user)
        setUpGeneralSection(state.user, state.bitcoinUnit, state.exchangeRateWindow)

        binding.settingsBitcoin.visibility = if (showBitcoinSettings(state)) View.VISIBLE else View.GONE

        binding.settingsDiagnostic.visibility =
            if (showDiagnosticSettings(state)) View.VISIBLE else View.GONE

        // Helper code for internal builds
        if (Globals.INSTANCE.isDogfood || Globals.INSTANCE.isDebug) {
            binding.internalDebuggingSection.visibility = View.VISIBLE

            if (state.overridableFeatures.isNotEmpty()) {
                binding.settingsDisableFeatureFlags.visibility = View.VISIBLE
                binding.settingsDisableFeatureFlags.setOnClickListener {
                    presenter.navigateToDisableFeatureFlags()
                }
            } else {
                binding.settingsDisableFeatureFlags.visibility = View.GONE
            }

            binding.settingsSendDebugData.visibility = View.VISIBLE
            binding.settingsSendDebugData.setOnClickListener { presenter.sendDebugData() }
        }
    }

    override fun setNightMode(mode: NightMode) {
        when (mode) {
            NightMode.DARK -> binding.settingsDarkMode.setDescription(getString(R.string.dark_mode_dark))
            NightMode.LIGHT -> binding.settingsDarkMode.setDescription(getString(R.string.dark_mode_light))
            NightMode.FOLLOW_SYSTEM -> setFollowSystemDarkMode()
        }
    }

    private fun setFollowSystemDarkMode() {
        when {
            supportsDarkMode() -> binding.settingsDarkMode.setDescription(R.string.dark_mode_follow_system)
            isInNightMode() -> setNightMode(NightMode.DARK)
            else -> setNightMode(NightMode.LIGHT)
        }
    }

    private fun setUpRecoveryAndLogoutSection(user: User) {
        if (user.isRecoverable) {
            setUpRecoverableUser(user)
        } else {
            setUpUnrecoverableUser()
        }
    }

    private fun setupSecuritySection() {
        if (!biometricsController.getAuthenticationStatus().canAuthenticate) {
            binding.securitySection.visibility = View.GONE
            return
        }

        binding.securitySection.visibility = View.VISIBLE
        binding.settingsBiometrics.setChecked(biometricsController.hasUserOptedInBiometrics())
        binding.settingsBiometrics.setOnCheckedChangeListener { switch, checked ->
            if (checked) {
                biometricsController.authenticate(
                    activity = requireActivity(),
                    promptTitle = getString(R.string.biometrics_setup_title),
                    promptSubtitle = getString(R.string.biometrics_setup_subtitle),
                    onSuccess = {
                        biometricsController.setUserOptInBiometrics(true)
                    },
                    onFailure = { error ->
                        switch.isChecked = false
                        parentActivity.showSnackBar(requireNotNull(error.message))
                    }
                )
            } else {
                biometricsController.setUserOptInBiometrics(false)
            }
        }
    }

    private fun setUpGeneralSection(
        user: User,
        bitcoinUnit: BitcoinUnit,
        rateWindow: ExchangeRateWindow,
    ) {

        val currency = Currency.getInfo(user.getPrimaryCurrency(rateWindow).currencyCode).get()
        setUpCurrency(currency, rateWindow)

        binding.settingsBitcoinUnit.setDescription(
            " " + MoneyHelper.formatCurrencyName(Currency.BTC, bitcoinUnit),
            R.drawable.btc_logo
        )
    }

    private fun setUpCurrency(currency: Currency, rateWindow: ExchangeRateWindow) {
        if (currency.flag != null) {
            binding.settingsPrimaryCurrency.setDescription(currency.flag + " " + currency.name)
        } else {
            if (currency.code == Currency.BTC.code) {
                binding.settingsPrimaryCurrency.setDescription(" ${currency.name}", R.drawable.btc_logo)
            } else {
                binding.settingsPrimaryCurrency.setDescription(" ${currency.name}", R.drawable.default_flag)
            }
        }

        binding.settingsPrimaryCurrency.setOnClickListener { editPrimaryCurrency(rateWindow) }
    }

    private fun setUpWalletDetailsSection(user: User) {
        if (user.hasP2PEnabled) {
            binding.walletDetailsSection.visibility = View.VISIBLE
            binding.settingsPhoneNumber.setDescription(user.phoneNumber.get().toE164String())
        } else {
            binding.walletDetailsSection.visibility = View.GONE
        }
    }

    private fun setUpPublicProfileSection(user: User) {
        if (user.profile.isPresent) {
            val profile = user.profile.get()
            binding.settingsProfilePicture.setPicture(profile.pictureUrl)
            binding.settingsUsername.text = profile.fullName
            binding.settingsHeaderSection.visibility = View.VISIBLE
        } else {
            hidePublicProfileSection()
        }
    }

    private fun setUpUnrecoverableUser() {
        binding.recoverySection.visibility = View.GONE
        binding.settingsLogout.visibility = View.GONE
    }

    private fun setUpRecoverableUser(user: User) {
        if (user.hasPassword) { // For now, only item in binding.recoverySection is ChangePassword
            binding.recoverySection.visibility = View.VISIBLE
        } else {
            binding.recoverySection.visibility = View.GONE
        }
        binding.settingsLogout.visibility = View.VISIBLE
        binding.settingsDeleteWallet.visibility = View.VISIBLE
    }

    override fun profilePictureUpdated(userProfile: UserProfile?) {
        if (userProfile != null) {
            binding.settingsProfilePicture.setPicture(userProfile.pictureUrl)
        } else {
            binding.settingsProfilePicture.clearPicture()
        }
    }

    override fun setLoading(loading: Boolean) {
        if (binding.settingsProfilePicture.isVisible) {
            binding.settingsProfilePicture.toggleLoading(loading)
            binding.settingsProfilePicture.resetPicture()
        }

        if (loading) {
            MuunDialog.Builder()
                .layout(R.layout.dialog_loading)
                .message(R.string.loading)
                .setCancelOnTouchOutside(false)
                .build()
                .let(this::showDialog)

            showingLoadingDialog = true
        } else if (showingLoadingDialog) {
            dismissDialog()
            showingLoadingDialog = false
        }
    }

    private fun editUsername() {
        presenter.navigateToEditUsername()
    }

    private fun editPassword() {
        presenter.navigateToEditPassword()
    }

    private fun editBitcoinUnit() {
        presenter.navigateToSelectBitcoinUnit()
    }

    private fun editDarkMode() {
        presenter.navigateToSelectDarkMode()
    }

    /**
     * Open Select Currency Activity, to change the user's primary currency.
     */
    private fun editPrimaryCurrency(rateWindow: ExchangeRateWindow) {
        val windowId = rateWindow.windowHid
        startActivityForResult(
            SelectCurrencyActivity.getSelectPrimaryCurrencyActivityIntent(parentActivity, windowId),
            REQUEST_NEW_PRIMARY_CURRENCY
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_NEW_PRIMARY_CURRENCY && resultCode == Activity.RESULT_OK) {
            val currencyCode = SelectCurrencyActivity.getResult(data)
            presenter.onPrimaryCurrencyChanged(Currency.getUnit(currencyCode).get())
        }
    }

    /**
     * Called when the user taps on the log out button.
     */
    private fun logout() {
        presenter.handleLogoutRequest()
    }

    /**
     * Called when the user taps on the delete wallet button.
     */
    private fun deleteWallet() {
        presenter.handleDeleteWalletRequest()
    }

    private fun goToLightningSettings() {
        presenter.navigateToLightningSettings()
    }

    private fun showDiagnosticSettings(state: SettingsState): Boolean =
        state.features.contains(MuunFeature.DIAGNOSTIC_MODE)

    private fun goToDiagnosticMode() {
        presenter.navigateToDiagnosticMode()
    }

    private fun showBitcoinSettings(state: SettingsState): Boolean =
        when (state.taprootFeatureStatus) {
            PREACTIVATED, SCHEDULED_ACTIVATION, ACTIVE -> true
            else -> false
        }

    private fun goToBitcoinSettings() {
        presenter.navigateToBitcoinSettings()
    }

    /**
     * Handle the logout tap action.
     */
    override fun handleLogout(displayExplanation: Boolean) {
        if (displayExplanation) {
            val muunDialog = MuunDialog.Builder()
                .title(R.string.settings_logout_explanation_title)
                .message(R.string.settings_logout_explanation_description)
                .positiveButton(
                    R.string.settings_logout_explanation_action,
                    resources.getColor(R.color.red)
                )
                .build()
            showDialog(muunDialog)
        } else {
            showLogoutDialog()
        }
    }

    /**
     * Show a confirmation dialog, then log out.
     */
    private fun showLogoutDialog() {
        val muunDialog = MuunDialog.Builder()
            .layout(R.layout.dialog_custom_layout)
            .title(R.string.settings_logout_alert_title)
            .message(R.string.settings_logout_alert_body)
            .positiveButton(
                R.string.settings_logout_alert_yes,
                resources.getColor(R.color.red)
            ) { presenter.logoutUser() }
            .negativeButton(
                R.string.settings_logout_alert_no,
                resources.getColor(R.color.text_secondary_color)
            )
            .build()
        showDialog(muunDialog)
    }

    override fun handleDeleteWallet(canDeleteWallet: Boolean, isRecoverableUser: Boolean) {
        if (!canDeleteWallet) {
            showCantDeleteNonEmptyWalletDialog()
        } else {
            showDeleteWalletDialog(isRecoverableUser)
        }
    }

    override fun showCantDeleteNonEmptyWalletDialog() {
        val muunDialog = MuunDialog.Builder()
            .layout(R.layout.dialog_custom_layout)
            .title(R.string.settings_delete_wallet_explanation_title)
            .message(R.string.settings_delete_wallet_explanation_description)
            .positiveButton(
                R.string.settings_delete_wallet_explanation_action,
                resources.getColor(R.color.red)
            )
            .build()
        showDialog(muunDialog)
    }

    /**
     * Show a confirmation dialog, then delete wallet.
     */
    private fun showDeleteWalletDialog(isRecoverableUser: Boolean) {
        val messageResId = if (isRecoverableUser) {
            R.string.settings_delete_wallet_alert_body_recoverable_user
        } else {
            R.string.settings_delete_wallet_alert_body_unrecoverable_user
        }

        val muunDialog = MuunDialog.Builder()
            .layout(R.layout.dialog_custom_layout)
            .title(R.string.settings_delete_wallet_alert_title)
            .message(messageResId)
            .positiveButton(
                R.string.settings_delete_wallet_alert_yes,
                resources.getColor(R.color.red)
            ) { presenter.deleteWallet() }
            .negativeButton(
                R.string.settings_delete_wallet_alert_no,
                resources.getColor(R.color.text_secondary_color)
            )
            .build()
        showDialog(muunDialog)
    }

    private fun onPictureChange(uri: Uri) {
        showTextToast(getString(R.string.uploading_picture))
        presenter.reportPictureChange(uri)
    }

    private fun getVersionForDisplay(): String {
        val versionName = Globals.INSTANCE.versionName
        return if (Globals.INSTANCE.isDogfood) {
            "$versionName (${Globals.INSTANCE.versionCode})"
        } else if (Globals.INSTANCE.isProduction && Globals.INSTANCE.isRelease) {
            versionName
        } else {
            val commit = Globals.INSTANCE.commit
            val branchName = Globals.INSTANCE.branch
            val flavor = Globals.INSTANCE.flavor
            val buildType = Globals.INSTANCE.buildType
            "$versionName ($flavor-$buildType-$commit-$branchName)"
        }
    }
}
