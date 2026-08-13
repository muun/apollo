package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityShippingAddressBinding
import io.muun.apollo.presentation.app.Navigator
import io.muun.apollo.presentation.ui.security_cards.utils.addWebsitePill
import io.muun.apollo.presentation.ui.security_cards_country_picker.CountryPickerActivity
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider
import io.muun.apollo.presentation.ui.utils.doAfterTextAsStringChanged
import io.muun.apollo.presentation.ui.utils.getColorCompat
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.hideSoftInputMethod
import io.muun.apollo.presentation.ui.utils.setTextIfChanged
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import io.muun.apollo.presentation.ui.view.MuunHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ShippingAddressActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_COUNTRY_INFO = "countryInfo"
        private const val EXTRA_PROVIDER = "provider"
        fun getIntent(
            context: Context,
            countryInfo: CountryInfo,
            provider: SecurityCardProvider,
        ) = Intent(context, ShippingAddressActivity::class.java).apply {
            putExtra(EXTRA_COUNTRY_INFO, countryInfo)
            putExtra(EXTRA_PROVIDER, provider)
        }
    }

    private val countryInfoExtra: CountryInfo
        get() = requireNotNull(
            intent.getParcelableExtra(EXTRA_COUNTRY_INFO)
        )

    private val providerExtra: SecurityCardProvider
        get() = requireNotNull(
            intent.getParcelableExtra(EXTRA_PROVIDER)
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
            countryInfo = countryInfoExtra,
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
                viewModel.setShippingCountry(picked)
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
            }
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
            siteUrl = providerExtra.name,
            id = R.id.security_cards_checkout_website_pill,
            onClick = { showConnectedToWebsiteDialog() }
        )
    }

    private fun showConnectedToWebsiteDialog() {
        hideSoftInputMethod {
            ConnectedToWebsiteDialogFragment.show(
                fragmentManager = supportFragmentManager,
                websiteUrl = providerExtra.name,
                animationStartViewId = R.id.security_cards_checkout_website_pill,
            )
        }
    }

    private fun configureFormFields() {
        binding.editTextFullName.doAfterTextAsStringChanged(viewModel::setFullName)
        binding.editTextEmail.doAfterTextAsStringChanged(viewModel::setEmail)
        binding.editTextShippingAddress.doAfterTextAsStringChanged(viewModel::setShippingAddress)
        binding.textViewCountry.setOnClickListener {
            val currentCountry = viewModel.viewState.value.countryInfo
            navigator.navigateToCountryPickerForResult(
                this,
                currentCountry.code,
                countryPickerLauncher,
            )
        }
        binding.editTextCity.doAfterTextAsStringChanged(viewModel::setShippingCity)
        binding.editTextState.doAfterTextAsStringChanged(viewModel::setShippingState)
        binding.editTextZipCode.doAfterTextAsStringChanged(viewModel::setShippingZipCode)
    }

    private fun configureContinueButton() {
        binding.buttonContinue.setOnClickListener {
            // TODO: Navigate to checkout
        }
    }

    private fun handleViewState(viewState: ShippingAddressViewModel.ViewState) {
        binding.editTextFullName.setTextIfChanged(viewState.fullName)
        binding.editTextEmail.setTextIfChanged(viewState.email)
        binding.editTextShippingAddress.setTextIfChanged(viewState.shippingAddress)
        binding.textViewCountry.setTextWithoutFilteringIfChanged(
            "${viewState.countryInfo.flagEmoji} ${viewState.countryInfo.name}"
        )
        binding.editTextCity.setTextIfChanged(viewState.city)
        binding.editTextState.setTextIfChanged(viewState.state)
        binding.editTextZipCode.setTextIfChanged(viewState.zipCode)

        binding.buttonContinue.isEnabled = viewState.isFormComplete
    }
}

private fun AutoCompleteTextView.setTextWithoutFilteringIfChanged(value: String) {
    if (text.toString() == value) {
        return
    }

    setText(value, false)
}
