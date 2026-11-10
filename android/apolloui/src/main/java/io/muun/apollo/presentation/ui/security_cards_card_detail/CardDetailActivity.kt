package io.muun.apollo.presentation.ui.security_cards_card_detail

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityCardDetailBinding
import io.muun.apollo.databinding.ItemCardSpecBinding
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.app.Navigator
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.security_cards.SecurityCardProviderData
import io.muun.apollo.presentation.ui.security_cards.utils.setPrimaryButtonColor
import io.muun.apollo.presentation.ui.security_cards.utils.theme
import io.muun.apollo.presentation.ui.security_cards.utils.toCardCostRichText
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_marketplace.CurrencySelectionSharedViewModel
import io.muun.apollo.presentation.ui.security_cards_marketplace.CurrencySelectionSharedViewModelFactory
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.utils.capitalizedName
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.setTextColorCompat
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.common.utils.BitcoinUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class CardDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_COUNTRY_CODE = "country_code"
        private const val EXTRA_PROVIDER_UUID = "provider_uuid"
        private const val EXTRA_CARD_UUID = "card_uuid"
        private const val EXTRA_CURRENT_CURRENCY = "current_currency"

        fun getIntent(
            context: Context,
            countryCode: MarketplaceCountryCode,
            providerUuid: String,
            cardUuid: String,
            currentCurrency: CurrentCurrency,
        ): Intent {
            return Intent(context, CardDetailActivity::class.java).apply {
                putExtra(EXTRA_COUNTRY_CODE, countryCode.value)
                putExtra(EXTRA_PROVIDER_UUID, providerUuid)
                putExtra(EXTRA_CARD_UUID, cardUuid)
                putExtra(EXTRA_CURRENT_CURRENCY, currentCurrency as Parcelable)
            }
        }
    }

    private val countryCodeExtra: MarketplaceCountryCode
        get() = MarketplaceCountryCode(requireNotNull(intent.getStringExtra(EXTRA_COUNTRY_CODE)))

    private val providerUuidExtra: String
        get() = requireNotNull(intent.getStringExtra(EXTRA_PROVIDER_UUID))

    private val cardUuidExtra: String
        get() = requireNotNull(intent.getStringExtra(EXTRA_CARD_UUID))

    private val currentCurrencyExtra: CurrentCurrency
        get() = requireNotNull(intent.getParcelableExtra(EXTRA_CURRENT_CURRENCY))

    private val binding: ActivityCardDetailBinding by lazy {
        ActivityCardDetailBinding.inflate(layoutInflater)
    }

    @Inject
    lateinit var viewModelFactory: CardDetailViewModel.Factory

    @Inject
    lateinit var navigator: Navigator

    private val viewModel: CardDetailViewModel by viewModels {
        CardDetailViewModelFactory(
            countryCode = countryCodeExtra,
            cardUuid = cardUuidExtra,
            currentCurrency = currentCurrencyExtra,
            assistedFactory = viewModelFactory,
        )
    }

    @Inject
    lateinit var currencySelectionSharedViewModelFactory: CurrencySelectionSharedViewModel.Factory

    private val currencySelectionSharedViewModel: CurrencySelectionSharedViewModel by viewModels {
        CurrencySelectionSharedViewModelFactory(
            initialCurrentCurrency = currentCurrencyExtra,
            assistedFactory = currencySelectionSharedViewModelFactory,
        )
    }

    private var metalShimmerAutoPlayed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setWindowInsetsCompat()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        getComponent().inject(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.viewState.collectLatest(::handleViewState)
                }

                launch {
                    currencySelectionSharedViewModel.selectedCurrency.collectLatest {
                        viewModel.rotateCurrencyInFooterAmounts(it)
                    }
                }
            }
        }

        setupHeader()
        setupFullSpecs()
        setupFooter()
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

    private fun setupFullSpecs() {
        binding.textViewSeeFullSpecs.setOnClickListener {
            navigateToFullSpecs()
        }
    }

    private fun setupFooter() {
        binding.viewFooter.setOnClickListener {
            currencySelectionSharedViewModel.rotateCurrencySelection()
        }
        binding.buttonContinue.setOnClickListener { navigateToShippingAddress() }
    }

    private fun handleViewState(viewState: CardDetailViewModel.ViewState) {
        when (viewState) {
            CardDetailViewModel.ViewState.Loading -> Unit
            is CardDetailViewModel.ViewState.Data -> binding.handleData(viewState)
        }
    }

    private fun ActivityCardDetailBinding.handleData(
        viewState: CardDetailViewModel.ViewState.Data,
    ) {
        val providerPrimaryColor =
            viewState.provider.theme(this@CardDetailActivity).primaryColorHex

        textViewTitle.text = viewState.provider.name
        textViewDescription.text = SecurityCardProviderData.descriptionForUuid(
            this@CardDetailActivity,
            viewState.provider.uuid,
        )
        Glide.with(this@CardDetailActivity)
            .load(viewState.securityCard.imageUrl)
            .into(imageViewCard)

        specsContainer.removeAllViews()

        val isMetalCard = viewState.securityCard.material.isMetal

        // The shimmer auto-plays once per screen: Data re-emissions (e.g. currency rotation, or
        // returning from full specs) rebuild the spec views, and replaying on each of them would
        // be noise. Tap-to-replay stays armed on every rebuild.
        val materialShimmer = when {
            !isMetalCard -> SpecValueShimmer.NONE
            metalShimmerAutoPlayed -> SpecValueShimmer.ARMED
            else -> SpecValueShimmer.AUTO_PLAY
        }
        metalShimmerAutoPlayed =
            metalShimmerAutoPlayed || materialShimmer == SpecValueShimmer.AUTO_PLAY

        specsContainer.addView(createPrimarySpecView(
            iconRes = R.drawable.ic_box,
            labelRes = R.string.security_cards_card_detail_material_label,
            value = viewState.securityCard.material.capitalizedName(),
            valueColorRes = R.color.security_card_metal_text.takeIf { isMetalCard },
            shimmer = materialShimmer,
        ))
        specsContainer.addView(createPrimarySpecView(
            iconRes = R.drawable.ic_map_pin,
            labelRes = R.string.security_cards_card_detail_ships_from_label,
            value = CountryInfo(viewState.provider.shippingCountryCode)
                .name(this@CardDetailActivity),
        ))
        specsContainer.addView(createPrimarySpecView(
            iconRes = R.drawable.ic_clock_marketplace,
            labelRes = R.string.security_cards_card_detail_delivers_in_label,
            value = getString(
                R.string.security_cards_card_detail_delivers_in_value,
                viewState.provider.minShippingTimeDays,
                viewState.provider.maxShippingTimeDays,
            ),
        ))

        textViewSeeFullSpecs.setTextColor(providerPrimaryColor)

        val cardCost = when (viewState.footer.currentCurrency) {
            CurrentCurrency.PRIMARY -> viewState.footer.cardCost
            CurrentCurrency.BTC ->
                BitcoinUtils.satoshisToBitcoins(viewState.footer.cardCostInSats)
        }
        textViewFooterCardCost.text = cardCost.toCardCostRichText(this@CardDetailActivity)
        val shippingCost = when (viewState.footer.currentCurrency) {
            CurrentCurrency.PRIMARY -> viewState.footer.shippingAndTaxesCost
            CurrentCurrency.BTC ->
                BitcoinUtils.satoshisToBitcoins(viewState.footer.shippingAndTaxesCostInSats)
        }
        textViewFooterShippingCost.text = getString(
            R.string.security_cards_marketplace_shipping_and_taxes_cost,
            MoneyHelper.formatLongMonetaryAmount(shippingCost, BitcoinUnit.BTC, locale()),
        )

        buttonContinue.setPrimaryButtonColor(providerPrimaryColor)
        buttonContinue.text = getString(
            R.string.security_cards_card_detail_go_to_provider,
            viewState.provider.name.uppercase(),
        )
    }

    private fun createPrimarySpecView(
        @DrawableRes iconRes: Int,
        @StringRes labelRes: Int,
        value: String,
        @ColorRes valueColorRes: Int? = null,
        shimmer: SpecValueShimmer = SpecValueShimmer.NONE,
    ): View {
        return ItemCardSpecBinding.inflate(layoutInflater, binding.specsContainer, false).apply {
            imageViewIcon.setImageResource(iconRes)
            textViewLabel.setText(labelRes)
            textViewValue.text = value
            textViewValue.setTextColorCompat(valueColorRes ?: R.color.text_primary_color)
            if (shimmer != SpecValueShimmer.NONE) {
                textViewValue.armShimmer(autoPlay = shimmer == SpecValueShimmer.AUTO_PLAY)
            }
        }.root
    }

    /** Whether a spec value shimmers, and whether it plays on its own or only when tapped. */
    private enum class SpecValueShimmer {
        NONE,
        ARMED,
        AUTO_PLAY,
    }

    private fun navigateToFullSpecs() {
        navigator.navigateToSecurityCardsFullSpecs(
            this,
            countryCodeExtra,
            cardUuidExtra,
        )
    }

    private fun navigateToShippingAddress() {
        val data = viewModel.viewState.value as? CardDetailViewModel.ViewState.Data ?: return

        navigator.navigateToShippingAddress(
            this,
            countryCodeExtra,
            providerUuidExtra,
            data.provider.lightTheme.primaryColorHex,
            data.provider.darkTheme.primaryColorHex,
            data.provider.websiteUrl,
        )
    }
}
