package io.muun.apollo.presentation.ui.fragments.settings

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import io.muun.apollo.BuildConfig
import io.muun.apollo.R
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.domain.model.*
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.select_currency.SelectCurrencyActivity
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.utils.getCurrentNightMode
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.apollo.presentation.ui.view.MuunPictureInput
import io.muun.apollo.presentation.ui.view.MuunSettingItem
import io.muun.common.model.Currency

open class SettingsFragment: SingleFragment<SettingsPresenter>(), SettingsView {

    private val REQUEST_NEW_PRIMARY_CURRENCY = 4

    @BindView(R.id.settings_profile_picture)
    lateinit var muunPictureInput: MuunPictureInput

    @BindView(R.id.settings_username)
    lateinit var username: TextView

    @BindView(R.id.settings_header_section)
    lateinit var settingsHeaderSection: View

    @BindView(R.id.wallet_details_section)
    lateinit var wallet_details_section: View

    @BindView(R.id.settings_phone_number)
    lateinit var phoneNumberItem: MuunSettingItem

    @BindView(R.id.settings_bitcoin_unit)
    lateinit var bitcoinUnitItem: MuunSettingItem

    @BindView(R.id.settings_primary_currency)
    lateinit var currencyItem: MuunSettingItem

    @BindView(R.id.settings_dark_mode)
    lateinit var darkModeItem: MuunSettingItem

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
        super.initializeUi(view)

        // For PublicProfile, if need be we'll show it later (yes, this default is backwards)
        parentActivity.header.visibility = View.GONE

        // Tell parent activity we want a say in how, when and if menu items are/should be displayed
        setHasOptionsMenu(true)

        muunPictureInput.setOnErrorListener { error: UserFacingError? -> presenter.handleError(error) }
        muunPictureInput.setOnChangeListener { uri: Uri -> onPictureChange(uri) }
        versionCode.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
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

    override fun setUser(user: User, mode: CurrencyDisplayMode, rateWindow: ExchangeRateWindow) {
        setUpRecoveryAndLogoutSection(user)
        setUpPublicProfileSection(user)
        setUpWalletDetailsSection(user)
        setUpGeneralSection(user, mode, rateWindow)
    }

    override fun setNightMode(mode: NightMode) {
        when(mode) {
            NightMode.DARK -> darkModeItem.setDescription(getString(R.string.dark_mode_dark))
            NightMode.LIGHT -> darkModeItem.setDescription(getString(R.string.dark_mode_light))
            NightMode.FOLLOW_SYSTEM -> setFollowSystemDarkMode()
        }
    }

    private fun setFollowSystemDarkMode() {

        if (UiUtils.supportsDarkMode()) {
            darkModeItem.setDescription(getString(R.string.dark_mode_follow_system))

        } else {
            when (getCurrentNightMode()) {

                Configuration.UI_MODE_NIGHT_YES -> {
                    setNightMode(NightMode.DARK)
                }

                Configuration.UI_MODE_NIGHT_NO -> {
                    setNightMode(NightMode.LIGHT)
                }
            }
        }
    }

    private fun setUpRecoveryAndLogoutSection(user: User) {
        if (user.isRecoverable) {
            setUpRecoverableUser(user)
        } else {
            setUpUnrecoverableUser()
        }
    }

    private fun setUpGeneralSection(user: User,
                                    currencyMode: CurrencyDisplayMode,
                                    rateWindow: ExchangeRateWindow) {

        val currency = Currency.getInfo(user.getPrimaryCurrency(rateWindow).currencyCode).get()
        setUpCurrency(currency, currencyMode)

        bitcoinUnitItem.setDescription(
            " " + MoneyHelper.formatCurrencyName(Currency.BTC, currencyMode),
            R.drawable.btc_logo
        )
    }

    private fun setUpCurrency(currency: Currency, currencyMode: CurrencyDisplayMode) {
        val currencyName = MoneyHelper.formatCurrencyName(currency, currencyMode)
        if (currency.flag != null) {
            currencyItem.setDescription(currency.flag + " " + currencyName)
        } else {
            if (currency.code == Currency.BTC.code) {
                currencyItem.setDescription(" $currencyName", R.drawable.btc_logo)
            } else {
                currencyItem.setDescription(" $currencyName", R.drawable.default_flag)
            }
        }
    }

    private fun setUpWalletDetailsSection(user: User) {
        if (user.hasP2PEnabled) {
            wallet_details_section.visibility = View.VISIBLE
            phoneNumberItem.setDescription(user.phoneNumber.get().toE164String())
        } else {
            wallet_details_section.visibility = View.GONE
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

    @OnClick(R.id.settings_edit_username)
    fun editUsername() {
        presenter.navigateToEditUsername()
    }

    @OnClick(R.id.settings_password)
    fun editPassword() {
        presenter.navigateToEditPassword()
    }

    @OnClick(R.id.settings_bitcoin_unit)
    fun editBitcoinUnit() {
        presenter.navigateToSelectBitcoinUnit()
    }

    @OnClick(R.id.settings_dark_mode)
    fun editDarkMode() {
        presenter.navigateToSelectDarkMode()
    }

    /**
     * Open Select Currency Activity, to change the user's primary currency.
     */
    @OnClick(R.id.settings_primary_currency)
    fun editPrimaryCurrency() {
        startActivityForResult(
            SelectCurrencyActivity.getStartSelectPrimaryCurrencyActivityIntent(parentActivity),
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
    @OnClick(R.id.settings_logout)
    fun logout() {
        presenter.handleDeleteWalletRequest()
    }

    @OnClick(R.id.settings_lightning)
    fun lighting() {
        presenter.navigateToLightningSettings()
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

    /**
     * Handle the delete wallet tap action.
     */
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
}
