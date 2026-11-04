package io.muun.apollo.presentation.ui.security_cards_full_specs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivitySecurityCardsFullSpecsBinding
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.domain.model.marketplace.SecurityCardFullSpecs
import io.muun.apollo.domain.model.marketplace.SecurityCardSecureElement
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.security_cards.utils.theme
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_full_specs.models.AdditionalInfo
import io.muun.apollo.presentation.ui.utils.capitalizedName
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import io.muun.apollo.presentation.ui.utils.toCompactDecimalString
import io.muun.apollo.presentation.ui.view.MuunHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class SecurityCardsFullSpecsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_COUNTRY_CODE = "country_code"
        private const val EXTRA_CARD_UUID = "card_uuid"

        fun getIntent(
            context: Context,
            countryCode: MarketplaceCountryCode,
            cardUuid: String,
        ): Intent {
            return Intent(context, SecurityCardsFullSpecsActivity::class.java)
                .putExtra(EXTRA_COUNTRY_CODE, countryCode.value)
                .putExtra(EXTRA_CARD_UUID, cardUuid)
        }
    }

    private val countryCodeExtra: MarketplaceCountryCode
        get() = MarketplaceCountryCode(requireNotNull(intent.getStringExtra(EXTRA_COUNTRY_CODE)))

    private val cardUuidExtra: String
        get() = requireNotNull(intent.getStringExtra(EXTRA_CARD_UUID))

    private val binding: ActivitySecurityCardsFullSpecsBinding by lazy {
        ActivitySecurityCardsFullSpecsBinding.inflate(layoutInflater)
    }

    @Inject
    lateinit var viewModelFactory: SecurityCardsFullSpecsViewModel.Factory

    private val viewModel: SecurityCardsFullSpecsViewModel by viewModels {
        SecurityCardsFullSpecsViewModelFactory(
            countryCode = countryCodeExtra,
            cardUuid = cardUuidExtra,
            assistedFactory = viewModelFactory,
        )
    }

    private val adapter: SecurityCardsFullSpecsAdapter by lazy {
        SecurityCardsFullSpecsAdapter(onAdditionalInfoClick = ::showAdditionalInfoBottomSheet)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setWindowInsetsCompat()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        getComponent().inject(this)

        setupHeader()
        setupRecyclerView()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collectLatest(::handleViewState)
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

    private fun setupHeader() {
        binding.header.attachToActivity(this)
        binding.header.setNavigation(MuunHeader.Navigation.BACK)
    }

    private fun setupRecyclerView() {
        binding.recyclerView.adapter = adapter
    }

    private fun handleViewState(viewState: SecurityCardsFullSpecsViewModel.ViewState) {
        when (viewState) {
            SecurityCardsFullSpecsViewModel.ViewState.Loading -> Unit
            is SecurityCardsFullSpecsViewModel.ViewState.Data -> handleData(viewState)
        }
    }

    private fun handleData(viewState: SecurityCardsFullSpecsViewModel.ViewState.Data) {
        // Guard against re-emissions: repeatOnLifecycle re-collects the StateFlow on every
        // lifecycle restart, and stacked decorations would draw the surface twice
        if (binding.recyclerView.itemDecorationCount == 0) {
            binding.recyclerView.addItemDecoration(
                SpecSectionItemDecoration(
                    this,
                    viewState.fullSpecs.provider.theme(this).surfaceColorHex,
                )
            )
        }

        adapter.submitList(buildSpecList(viewState.fullSpecs))
    }

    private fun buildSpecList(fullSpecs: SecurityCardFullSpecs): List<SpecListItem> {
        return buildList {
            add(
                SpecListItem.CardImage(
                    imageUrl = fullSpecs.card.imageUrl,
                    cardHeight = fullSpecs.card.heightMm,
                    cardWidth = fullSpecs.card.widthMm,
                    arrowColor = fullSpecs.provider.theme(this@SecurityCardsFullSpecsActivity)
                        .primaryColorHex,
                )
            )

            add(
                SpecListItem.SectionHeader(
                    getString(R.string.security_cards_full_specs_section_specifications)
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_box,
                    label = getString(R.string.security_cards_full_specs_material_label),
                    value = fullSpecs.card.material.capitalizedName(),
                    isMetalValue = fullSpecs.card.material.isMetal,
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_line_weight,
                    label = getString(R.string.security_cards_full_specs_thickness_label),
                    value = getString(
                        R.string.security_cards_full_specs_thickness_value,
                        fullSpecs.card.thicknessMm.toCompactDecimalString(locale()),
                    )
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_weight,
                    label = getString(R.string.security_cards_full_specs_weight_label),
                    value = getString(
                        R.string.security_cards_full_specs_weight_value,
                        fullSpecs.card.weightGrams,
                    )
                )
            )

            add(
                SpecListItem.SectionHeader(
                    getString(R.string.security_cards_full_specs_section_security)
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_shield,
                    label = getString(R.string.security_cards_full_specs_secure_element_label),
                    value = fullSpecs.card.secureElement.displayName(),
                    additionalInfo = AdditionalInfo(
                        iconRes = R.drawable.ic_shield,
                        title = getString(R.string.security_cards_full_specs_secure_element_label),
                        bodyHtml = getString(
                            R.string.security_cards_full_specs_secure_element_additional_data
                        ),
                    ),
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_code,
                    label = getString(R.string.security_cards_full_specs_firmware_label),
                    value = getString(R.string.security_cards_full_specs_firmware_value),
                    additionalInfo = AdditionalInfo(
                        iconRes = R.drawable.ic_code,
                        title = getString(R.string.security_cards_full_specs_firmware_label),
                        bodyHtml = getString(
                            R.string.security_cards_full_specs_firmware_additional_data
                        ),
                    ),
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_box,
                    label = getString(R.string.security_cards_full_specs_packaging_label),
                    value = getString(R.string.security_cards_full_specs_packaging_value),
                )
            )

            add(
                SpecListItem.SectionHeader(
                    getString(R.string.security_cards_full_specs_section_delivery)
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_truck,
                    label = getString(R.string.security_cards_full_specs_shipped_by_label),
                    value = fullSpecs.provider.name,
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_map_pin,
                    label = getString(R.string.security_cards_full_specs_from_label),
                    value = CountryInfo(fullSpecs.provider.shippingCountryCode)
                        .name(this@SecurityCardsFullSpecsActivity),
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_clock_marketplace,
                    label = getString(R.string.security_cards_full_specs_arrives_in_label),
                    value = getString(
                        R.string.security_cards_full_specs_arrives_in_value,
                        fullSpecs.provider.minShippingTimeDays,
                        fullSpecs.provider.maxShippingTimeDays,
                    ),
                )
            )
            add(
                SpecListItem.Row(
                    iconRes = R.drawable.ic_broom,
                    label = getString(R.string.security_cards_full_specs_shipping_data_label),
                    value = getString(R.string.security_cards_full_specs_shipping_data_value),
                    additionalInfo = AdditionalInfo(
                        iconRes = R.drawable.ic_broom,
                        title = getString(R.string.security_cards_full_specs_shipping_data_label),
                        bodyHtml = getString(
                            R.string.security_cards_full_specs_shipping_data_additional_data
                        ),
                    ),
                )
            )
        }
    }

    private fun showAdditionalInfoBottomSheet(info: AdditionalInfo) {
        SpecAdditionalInfoBottomSheetDialogFragment.newInstance(info)
            .show(
                supportFragmentManager,
                SpecAdditionalInfoBottomSheetDialogFragment::class.java.simpleName,
            )
    }
}

private fun SecurityCardSecureElement.displayName(): String {
    return when (this) {
        SecurityCardSecureElement.EAL_5 -> "EAL 5"
        SecurityCardSecureElement.EAL_5_PLUS -> "EAL 5+"
        SecurityCardSecureElement.EAL_6 -> "EAL 6"
        SecurityCardSecureElement.EAL_6_PLUS -> "EAL 6+"
    }
}
