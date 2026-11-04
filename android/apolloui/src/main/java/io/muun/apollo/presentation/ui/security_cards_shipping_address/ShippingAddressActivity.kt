package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputLayout
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityShippingAddressBinding
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.presentation.app.Navigator
import io.muun.apollo.presentation.ui.security_cards.utils.addWebsitePill
import io.muun.apollo.presentation.ui.security_cards.utils.setPrimaryButtonColor
import io.muun.apollo.presentation.ui.security_cards_country_picker.CountryPickerActivity
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_shipping_address.ShippingAddressViewModel.ViewState
import io.muun.apollo.presentation.ui.utils.doAfterTextAsStringChanged
import io.muun.apollo.presentation.ui.utils.getColorCompat
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.hideSoftInputMethod
import io.muun.apollo.presentation.ui.utils.isInNightMode
import io.muun.apollo.presentation.ui.utils.setTextIfChanged
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import io.muun.apollo.presentation.ui.view.MuunHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

class ShippingAddressActivity : AppCompatActivity() {

    companion object {
        // Matches the prototype's timeout for auto-opening the connected-to-website dialog.
        private const val AUTO_SHOW_CONNECTED_DIALOG_DELAY_MS = 110L

        private const val EXTRA_COUNTRY_CODE = "country_code"
        private const val EXTRA_PROVIDER_UUID = "provider_uuid"
        private const val EXTRA_PROVIDER_LIGHT_PRIMARY_COLOR = "provider_light_primary_color"
        private const val EXTRA_PROVIDER_DARK_PRIMARY_COLOR = "provider_dark_primary_color"
        private const val EXTRA_PROVIDER_WEBSITE_URL = "provider_website_url"

        fun getIntent(
            context: Context,
            countryCode: MarketplaceCountryCode,
            providerUuid: String,
            @ColorInt providerLightPrimaryColor: Int,
            @ColorInt providerDarkPrimaryColor: Int,
            providerWebsiteUrl: String,
        ): Intent {
            return Intent(context, ShippingAddressActivity::class.java).apply {
                putExtra(EXTRA_COUNTRY_CODE, countryCode.value)
                putExtra(EXTRA_PROVIDER_UUID, providerUuid)
                putExtra(EXTRA_PROVIDER_LIGHT_PRIMARY_COLOR, providerLightPrimaryColor)
                putExtra(EXTRA_PROVIDER_DARK_PRIMARY_COLOR, providerDarkPrimaryColor)
                putExtra(EXTRA_PROVIDER_WEBSITE_URL, providerWebsiteUrl)
            }
        }
    }

    private val countryCodeExtra: MarketplaceCountryCode
        get() = MarketplaceCountryCode(
            requireNotNull(intent.getStringExtra(EXTRA_COUNTRY_CODE))
        )

    @get:ColorInt
    private val providerPrimaryColorExtra: Int
        get() {
            val extra = if (isInNightMode()) {
                EXTRA_PROVIDER_DARK_PRIMARY_COLOR
            } else {
                EXTRA_PROVIDER_LIGHT_PRIMARY_COLOR
            }

            return intent.getIntExtra(extra, 0)
        }

    private val providerUuidExtra: String
        get() = requireNotNull(
            intent.getStringExtra(EXTRA_PROVIDER_UUID)
        )

    private val providerWebsiteUrlExtra: String
        get() = requireNotNull(
            intent.getStringExtra(EXTRA_PROVIDER_WEBSITE_URL)
        )

    private val binding: ActivityShippingAddressBinding by lazy {
        ActivityShippingAddressBinding.inflate(layoutInflater)
    }

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var viewModelFactory: ShippingAddressViewModel.Factory

    private val viewModel: ShippingAddressViewModel by viewModels {
        ShippingAddressViewModelFactory(
            countryCode = countryCodeExtra,
            providerUuid = providerUuidExtra,
            assistedFactory = viewModelFactory,
            owner = this,
        )
    }

    private val countryPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val picked = CountryPickerActivity.getResult(
                    requireNotNull(result.data)
                )
                viewModel.setShippingCountryCode(picked.code)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setWindowInsetsCompat()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        getComponent().inject(this)

        configureHeader()
        configureFormFields()
        configureContinueButton()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.viewState.collectLatest(::handleViewState)
                }

                launch {
                    viewModel.viewEvent.collectLatest(::handleViewEvent)
                }
            }
        }

        if (viewModel.shouldShowConnectedToWebsiteDialogOnStart()) {
            autoShowConnectedToWebsiteDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // EditText regain focus upon screen recreation (for instance screen rotation),
        // if dialog is being shown we have to hide the soft keyboard.
        if (ConnectedToWebsiteDialogFragment.isShown(supportFragmentManager)) {
            hideSoftInputMethod()
        }
    }

    /**
     * Opens the connected-to-website dialog on its own: once the screen is laid out (the dialog
     * expands out of the header's pill, and TransitionManager silently skips the animation if
     * the scene isn't laid out when it starts), after the delay the designs call for. The delay
     * only counts while the screen is STARTED — flowWithLifecycle cancels the countdown when
     * the app goes to background and restarts it on return, so the dialog never opens over a
     * screen the user isn't watching. A re-created activity (e.g. rotation) mid-wait just runs
     * this again: the provider is acknowledged as seen only when the dialog actually shows.
     */
    private fun autoShowConnectedToWebsiteDialog() {
        binding.root.doOnLayout {
            lifecycleScope.launch {
                flow {
                    delay(AUTO_SHOW_CONNECTED_DIALOG_DELAY_MS)
                    emit(Unit)
                }
                    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                    .first()

                if (!ConnectedToWebsiteDialogFragment.isShown(supportFragmentManager)) {
                    viewModel.saveDontShowConnectedToWebsiteDialogOnStart()
                    showConnectedToWebsiteDialog()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun configureHeader() {
        binding.header.attachToActivity(this)
        binding.header.setBackgroundColor(getColorCompat(R.color.surface_background))
        binding.header.setNavigation(MuunHeader.Navigation.BACK)
        binding.header.hideTitle()

        binding.header.addWebsitePill(
            siteUrl = providerWebsiteUrlExtra,
            id = R.id.security_cards_checkout_website_pill,
            onClick = { showConnectedToWebsiteDialog() }
        )
    }

    private fun showConnectedToWebsiteDialog() {
        hideSoftInputMethod {
            ConnectedToWebsiteDialogFragment.show(
                fragmentManager = supportFragmentManager,
                websiteUrl = providerWebsiteUrlExtra,
                animationStartViewId = R.id.security_cards_checkout_website_pill,
            )
        }
    }

    private fun configureFormFields() {
        binding.editTextFullName.doAfterTextAsStringChanged(viewModel::setFullName)
        binding.editTextEmail.doAfterTextAsStringChanged(viewModel::setEmail)
        binding.editTextAddress1.doAfterTextAsStringChanged(viewModel::setAddress1)
        binding.editTextAddress2.doAfterTextAsStringChanged(viewModel::setAddress2)
        binding.textViewCountry.setOnClickListener {
            val currentCountryCode = viewModel.viewState.value.countryCode
            navigator.navigateToCountryPickerForResult(
                this,
                currentCountryCode,
                providerUuidExtra,
                countryPickerLauncher,
            )
        }
        binding.editTextCity.doAfterTextAsStringChanged(viewModel::setShippingCity)
        binding.editTextState.doAfterTextAsStringChanged(viewModel::setShippingState)
        binding.editTextZipCode.doAfterTextAsStringChanged(viewModel::setShippingZipCode)
    }

    private fun configureContinueButton() {
        binding.buttonContinue.setPrimaryButtonColor(providerPrimaryColorExtra)
        binding.buttonContinue.setOnClickListener {
            hideSoftInputMethod()
            viewModel.submit()
        }
    }

    private fun handleViewState(viewState: ShippingAddressViewModel.ViewState) {
        binding.editTextFullName.setTextIfChanged(viewState.fullName.value)
        binding.editTextEmail.setTextIfChanged(viewState.email.value)
        binding.editTextAddress1.setTextIfChanged(viewState.address1.value)
        binding.editTextAddress2.setTextIfChanged(viewState.address2.value)
        CountryInfo(viewState.countryCode).let { country ->
            binding.textViewCountry.setTextWithoutFilteringIfChanged(
                "${country.flagEmoji} ${country.name(this)}"
            )
        }
        binding.editTextCity.setTextIfChanged(viewState.city.value)
        binding.editTextState.setTextIfChanged(viewState.state.value)
        binding.editTextZipCode.setTextIfChanged(viewState.zipCode.value)

        fieldLayouts.forEach { (field, layout) ->
            val message = field(viewState).error?.let(::getString)

            if (layout.error?.toString() != message) {
                layout.error = message
            }
        }
    }

    private fun handleViewEvent(viewEvent: ShippingAddressViewModel.ViewEvent) {
        when (viewEvent) {
            ShippingAddressViewModel.ViewEvent.NavigateToCheckout -> {
                // TODO: Navigate to checkout
            }

            ShippingAddressViewModel.ViewEvent.ScrollToFirstError -> scrollToFirstFieldError()
        }
    }

    // region: Form error handling
    /** The layout hosting each validated form field, in the form's visual order. */
    private val fieldLayouts: List<Pair<(ViewState) -> FormItemValue, TextInputLayout>> by lazy {
        listOf(
            ViewState::email to binding.layoutEmail,
            ViewState::fullName to binding.layoutFullName,
            ViewState::address1 to binding.layoutAddress1,
            ViewState::city to binding.layoutCity,
            ViewState::state to binding.layoutState,
            ViewState::zipCode to binding.layoutZipCode,
        )
    }

    private fun scrollToFirstFieldError() {
        val viewState = viewModel.viewState.value
        val firstErrorLayout = fieldLayouts
            .firstOrNull { (field, _) -> field(viewState).error != null }
            ?.second
            ?: return

        binding.scrollView.smoothScrollTo(0, firstErrorLayout.top)
    }
    // endregion
}

private fun AutoCompleteTextView.setTextWithoutFilteringIfChanged(value: String) {
    if (text.toString() == value) {
        return
    }

    setText(value, false)
}
