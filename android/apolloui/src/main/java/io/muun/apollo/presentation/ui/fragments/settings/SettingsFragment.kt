package io.muun.apollo.presentation.ui.fragments.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.BuildConfig
import io.muun.apollo.R
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.apollo.domain.model.NightMode
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.ACTIVE
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.CAN_ACTIVATE
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.CAN_PREACTIVATE
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.OFF
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.PREACTIVATED
import io.muun.apollo.domain.model.UserActivatedFeatureStatus.SCHEDULED_ACTIVATION
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.model.user.UserProfile
import io.muun.apollo.domain.selector.BlockchainHeightSelector
import io.muun.apollo.domain.selector.UserActivatedFeatureStatusSelector
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.fragments.settings.SettingsPresenter.SettingsState
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.select_currency.SelectCurrencyActivity
import io.muun.apollo.presentation.ui.utils.isInNightMode
import io.muun.apollo.presentation.ui.utils.supportsDarkMode
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.apollo.presentation.ui.view.MuunIconButton
import io.muun.apollo.presentation.ui.view.MuunPictureInput
import io.muun.apollo.presentation.ui.view.MuunSettingItem
import io.muun.common.model.Currency

open class SettingsFragment : SingleFragment<SettingsPresenter>(), SettingsView {

    private val REQUEST_NEW_PRIMARY_CURRENCY = 4

    @BindView(R.id.settings_profile_picture)
    lateinit var muunPictureInput: MuunPictureInput

    @BindView(R.id.settings_username)
    lateinit var username: TextView

    @BindView(R.id.settings_header_section)
    lateinit var settingsHeaderSection: View

    @BindView(R.id.wallet_details_section)
    lateinit var walletDetailsSection: View

    @BindView(R.id.settings_edit_username)
    lateinit var usernameItem: MuunIconButton

    @BindView(R.id.settings_password)
    lateinit var passwordItem: MuunSettingItem

    @BindView(R.id.settings_phone_number)
    lateinit var phoneNumberItem: MuunSettingItem

    @BindView(R.id.settings_bitcoin_unit)
    lateinit var bitcoinUnitItem: MuunSettingItem

    @BindView(R.id.settings_primary_currency)
    lateinit var currencyItem: MuunSettingItem

    @BindView(R.id.settings_dark_mode)
    lateinit var darkModeItem: MuunSettingItem

    @BindView(R.id.settings_bitcoin)
    lateinit var bitcoinSettingsItem: MuunSettingItem

    @BindView(R.id.settings_lightning)
    lateinit var lightningSettingsItem: MuunSettingItem

    @BindView(R.id.settings_logout)
    lateinit var logoutItem: View

    @BindView(R.id.recovery_section)
    lateinit var recoverySection: View

    @BindView(R.id.log_out_text_view)
    lateinit var logOutTextView: TextView

    @BindView(R.id.settings_version_code)
    lateinit var versionCode: TextView

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.fragment_settings

    override fun initializeUi(view: View) {
        // Tell parent activity we want a say in how, when and if menu items are/should be displayed
        setHasOptionsMenu(true)

        muunPictureInput.setOnErrorListener { error: UserFacingError? -> presenter.handleError(error) }
        muunPictureInput.setOnChangeListener { uri: Uri -> onPictureChange(uri) }
        versionCode.text = getString(R.string.settings_version, getVersionForDisplay())

        usernameItem.setOnClickListener { editUsername() }
        passwordItem.setOnClickListener { editPassword() }
        darkModeItem.setOnClickListener { editDarkMode() }
        bitcoinUnitItem.setOnClickListener { editBitcoinUnit() }
        logoutItem.setOnClickListener { goToLogout() }
        bitcoinSettingsItem.setOnClickListener { goToBitcoinSettings() }
        lightningSettingsItem.setOnClickListener { goToLightningSettings() }

        if (Globals.INSTANCE.isDebugBuild) {
            // TEMP: code for Taproot QA:
//            versionCode.setOnClickListener { rotateDebugTaprootStatusForQa() }
            versionCode.setOnClickListener { presenter.openDebugPanel() }
        }
    }

    override fun setUpHeader() {
        // For PublicProfile, if need be we'll show it later (yes, this default is backwards)
        parentActivity.header.visibility = View.GONE
    }

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
        settingsHeaderSection.visibility = View.GONE
    }

    override fun setState(state: SettingsState) {
        setUpRecoveryAndLogoutSection(state.user)
        setUpPublicProfileSection(state.user)
        setUpWalletDetailsSection(state.user)
        setUpGeneralSection(state.user, state.bitcoinUnit, state.exchangeRateWindow)

        bitcoinSettingsItem.visibility = if (showBitcoinSettings(state)) View.VISIBLE else View.GONE
    }

    override fun setNightMode(mode: NightMode) {
        when (mode) {
            NightMode.DARK -> darkModeItem.setDescription(getString(R.string.dark_mode_dark))
            NightMode.LIGHT -> darkModeItem.setDescription(getString(R.string.dark_mode_light))
            NightMode.FOLLOW_SYSTEM -> setFollowSystemDarkMode()
        }
    }

    private fun setFollowSystemDarkMode() {
        when {
            supportsDarkMode() -> darkModeItem.setDescription(R.string.dark_mode_follow_system)
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

    private fun setUpGeneralSection(
        user: User,
        bitcoinUnit: BitcoinUnit,
        rateWindow: ExchangeRateWindow,
    ) {

        val currency = Currency.getInfo(user.getPrimaryCurrency(rateWindow).currencyCode).get()
        setUpCurrency(currency, rateWindow)

        bitcoinUnitItem.setDescription(
            " " + MoneyHelper.formatCurrencyName(Currency.BTC, bitcoinUnit),
            R.drawable.btc_logo
        )
    }

    private fun setUpCurrency(currency: Currency, rateWindow: ExchangeRateWindow) {
        if (currency.flag != null) {
            currencyItem.setDescription(currency.flag + " " + currency.name)
        } else {
            if (currency.code == Currency.BTC.code) {
                currencyItem.setDescription(" ${currency.name}", R.drawable.btc_logo)
            } else {
                currencyItem.setDescription(" ${currency.name}", R.drawable.default_flag)
            }
        }

        currencyItem.setOnClickListener { editPrimaryCurrency(rateWindow) }
    }

    private fun setUpWalletDetailsSection(user: User) {
        if (user.hasP2PEnabled) {
            walletDetailsSection.visibility = View.VISIBLE
            phoneNumberItem.setDescription(user.phoneNumber.get().toE164String())
        } else {
            walletDetailsSection.visibility = View.GONE
        }
    }

    private fun setUpPublicProfileSection(user: User) {
        if (user.profile.isPresent) {
            val profile = user.profile.get()
            muunPictureInput.setPicture(profile.pictureUrl)
            username.text = profile.fullName
            settingsHeaderSection.visibility = View.VISIBLE
        } else {
            hidePublicProfileSection()
        }
    }

    private fun setUpUnrecoverableUser() {
        recoverySection.visibility = View.GONE
        logOutTextView.setText(R.string.settings_delete_wallet)
    }

    private fun setUpRecoverableUser(user: User) {
        if (user.hasPassword) { // For now, only item in recoverySection is ChangePassword
            recoverySection.visibility = View.VISIBLE
        } else {
            recoverySection.visibility = View.GONE
        }
        logOutTextView.setText(R.string.settings_logout)
    }

    override fun profilePictureUpdated(userProfile: UserProfile?) {
        if (userProfile != null) {
            muunPictureInput.setPicture(userProfile.pictureUrl)
        } else {
            muunPictureInput.clearPicture()
        }
    }

    override fun setLoading(loading: Boolean) {
        muunPictureInput.toggleLoading(loading)
        muunPictureInput.resetPicture()
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
     * Called when the user taps on the log out or delete wallet button.
     */
    private fun goToLogout() {
        presenter.handleDeleteWalletRequest()
    }

    private fun goToLightningSettings() {
        presenter.navigateToLightningSettings()
    }

    private fun showBitcoinSettings(state: SettingsState): Boolean =
        when (state.taprootFeatureStatus) {
            PREACTIVATED,
            SCHEDULED_ACTIVATION,
            ACTIVE,
            -> true
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
                .positiveButton(R.string.settings_logout_explanation_action, null)
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
            .layout(R.layout.dialog_custom_layout2)
            .title(R.string.settings_logout_alert_title)
            .message(R.string.settings_logout_alert_body)
            .positiveButton(R.string.settings_logout_alert_yes) { presenter.logout() }
            .negativeButton(R.string.settings_logout_alert_no, null)
            .build()
        showDialog(muunDialog)
    }

    override fun handleDeleteWallet(displayExplanation: Boolean) {
        if (displayExplanation) {
            val muunDialog = MuunDialog.Builder()
                .layout(R.layout.dialog_custom_layout2)
                .title(R.string.settings_delete_wallet_explanation_title)
                .message(R.string.settings_delete_wallet_explanation_description)
                .positiveButton(R.string.settings_delete_wallet_explanation_action, null)
                .build()
            showDialog(muunDialog)
        } else {
            showDeleteWalletDialog()
        }
    }

    /**
     * Show a confirmation dialog, then delete wallet.
     */
    private fun showDeleteWalletDialog() {
        val muunDialog = MuunDialog.Builder()
            .layout(R.layout.dialog_custom_layout2)
            .title(R.string.settings_delete_wallet_alert_title)
            .message(R.string.settings_delete_wallet_alert_body)
            .positiveButton(R.string.settings_delete_wallet_alert_yes) { presenter.deleteWallet() }
            .negativeButton(R.string.settings_delete_wallet_alert_no, null)
            .build()
        showDialog(muunDialog)
    }

    private fun onPictureChange(uri: Uri) {
        showTextToast(getString(R.string.uploading_picture))
        presenter.reportPictureChange(uri)
    }

    private fun getVersionForDisplay(): String {
        val versionName = BuildConfig.VERSION_NAME
        return if (BuildConfig.PRODUCTION && BuildConfig.RELEASE) {
            versionName
        } else {
            val commit = BuildConfig.COMMIT
            val branchName = BuildConfig.BRANCH
            "$versionName (${BuildConfig.FLAVOR}-${BuildConfig.BUILD_TYPE}-$commit-$branchName)"
        }
    }

    private fun rotateDebugTaprootStatusForQa() {
        val nextStatus = when (UserActivatedFeatureStatusSelector.DEBUG_TAPROOT_STATUS) {
            null -> OFF
            OFF -> CAN_PREACTIVATE
            CAN_PREACTIVATE -> CAN_ACTIVATE
            CAN_ACTIVATE -> PREACTIVATED
            PREACTIVATED -> SCHEDULED_ACTIVATION
            SCHEDULED_ACTIVATION -> ACTIVE
            ACTIVE -> null
        }

        val nextBlocksToTaproot = when (nextStatus) {
            null -> null
            OFF -> 1111
            CAN_PREACTIVATE -> 1112
            CAN_ACTIVATE -> 1113
            PREACTIVATED -> 1114
            SCHEDULED_ACTIVATION -> 1115
            ACTIVE -> 0
        }

        val nextToast = if (nextStatus != null) {
            "Taproot: ${nextStatus.name} / $nextBlocksToTaproot blocks"
        } else {
            "Taproot debug disabled"
        }

        UserActivatedFeatureStatusSelector.DEBUG_TAPROOT_STATUS = nextStatus
        BlockchainHeightSelector.DEBUG_BLOCKS_TO_TAPROOT = nextBlocksToTaproot

        showTextToast(nextToast)

        when (nextStatus) {
            CAN_PREACTIVATE -> presenter.showPreactivationNotification()
            ACTIVE -> presenter.showActivatedNotification()
            else -> {}
        }
    }
}
