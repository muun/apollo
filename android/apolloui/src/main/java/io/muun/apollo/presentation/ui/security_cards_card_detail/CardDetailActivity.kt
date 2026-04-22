package io.muun.apollo.presentation.ui.security_cards_card_detail

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
import io.muun.apollo.databinding.ActivityCardDetailBinding
import io.muun.apollo.databinding.ItemCardSpecBinding
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.selector.ExchangeRateSelector
import io.muun.apollo.domain.selector.UserSelector
import io.muun.apollo.presentation.app.ApolloApplication
import io.muun.apollo.presentation.ui.new_operation.toRichText
import io.muun.apollo.presentation.ui.security_cards_marketplace.CurrencySelectionSharedViewModel
import io.muun.apollo.presentation.ui.security_cards_marketplace.CurrencySelectionSharedViewModelFactory
import io.muun.apollo.presentation.ui.security_cards_marketplace.inBtc
import io.muun.apollo.presentation.ui.security_cards_marketplace.inCurrencyUnit
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import io.muun.apollo.presentation.ui.view.MuunHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class CardDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PROVIDER = "provider"
        private const val EXTRA_CARD = "card"
        private const val EXTRA_FOOTER = "footer"

        fun getIntent(
            context: Context,
            provider: SecurityCardProvider,
            card: SecurityCard,
            footer: MarketplaceFooter,
        ) = Intent(context, CardDetailActivity::class.java).apply {
            putExtra(EXTRA_PROVIDER, provider)
            putExtra(EXTRA_CARD, card)
            putExtra(EXTRA_FOOTER, footer)
        }
    }

    private val binding: ActivityCardDetailBinding by lazy {
        ActivityCardDetailBinding.inflate(layoutInflater)
    }

    @Inject
    lateinit var viewModelFactory: CardDetailViewModel.Factory
    
    private val viewModel: CardDetailViewModel by viewModels {
        CardDetailViewModelFactory(
            provider = requireNotNull(intent.getParcelableExtra(EXTRA_PROVIDER)),
            card = requireNotNull(intent.getParcelableExtra(EXTRA_CARD)),
            footer = requireNotNull(intent.getParcelableExtra(EXTRA_FOOTER)),
            assistedFactory = viewModelFactory,
        )
    }

    @Inject
    lateinit var currencySelectionSharedViewModelFactory: CurrencySelectionSharedViewModel.Factory

    private val currencySelectionSharedViewModel: CurrencySelectionSharedViewModel by viewModels {
        CurrencySelectionSharedViewModelFactory(
            initialCurrentCurrency = requireNotNull(intent.getParcelableExtra<MarketplaceFooter>(EXTRA_FOOTER)).currentCurrency,
            assistedFactory = currencySelectionSharedViewModelFactory,
        )
    }

    @Inject
    lateinit var exchangeRateSelector: ExchangeRateSelector

    @Inject
    lateinit var userSel: UserSelector

    override fun onCreate(savedInstanceState: Bundle?) {
        setWindowInsetsCompat()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        (applicationContext as ApolloApplication).getApplicationComponent().activityComponent().inject(this)

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
        binding.buttonContinue.setOnClickListener { navigateToProviderWebsite() }
    }

    private fun handleViewState(viewState: CardDetailViewModel.ViewState) {
        binding.textViewTitle.text = viewState.provider.name
        binding.textViewDescription.text = viewState.provider.description
        binding.imageViewCard.setImageResource(viewState.card.imageRes)

        binding.specsContainer.removeAllViews()
        viewState.card.primarySpecs.forEach { spec ->
            val specBinding = ItemCardSpecBinding.inflate(layoutInflater, binding.specsContainer, false)
            specBinding.imageViewIcon.setImageResource(spec.iconRes)
            specBinding.textViewLabel.text = spec.label
            specBinding.textViewValue.text = spec.value
            binding.specsContainer.addView(specBinding.root)
        }

        val rateProvider = exchangeRateSelector.watchLatest()
            .toBlocking()
            .first()

        binding.textViewFooterCardCost.text = getString(
            R.string.security_cards_marketplace_card_cost,
            toRichText(
                amt = when (viewState.footer.currentCurrency) {
                    CurrentCurrency.PRIMARY -> viewState.footer.cardCost.inCurrencyUnit(
                        rateProvider,
                        userSel.get().getPrimaryCurrency(rateProvider)
                    )
                    CurrentCurrency.BTC -> viewState.footer.cardCost.inBtc(rateProvider)
                },
                btcUnit = BitcoinUnit.BTC,
                isValid = true,
            )
        )
        binding.textViewFooterShippingCost.text = getString(
            R.string.security_cards_marketplace_shipping_and_taxes_cost,
            toRichText(
                amt = when (viewState.footer.currentCurrency) {
                    CurrentCurrency.PRIMARY -> viewState.footer.shippingAndTaxesCost.inCurrencyUnit(
                        rateProvider,
                        userSel.get().getPrimaryCurrency(rateProvider)
                    )
                    CurrentCurrency.BTC -> viewState.footer.shippingAndTaxesCost.inBtc(
                        rateProvider
                    )
                },
                btcUnit = BitcoinUnit.BTC,
                isValid = true,
            )
        )

        binding.buttonContinue.text = getString(
            R.string.security_cards_card_detail_go_to_provider,
            viewState.provider.name.uppercase(),
        )
    }

    private fun navigateToFullSpecs() {
        // TODO: Navigate to full specs activity
    }

    private fun navigateToProviderWebsite() {
        // TODO: Navigate to provider website
    }
}
