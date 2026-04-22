package io.muun.apollo.presentation.ui.security_cards_marketplace

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentSecurityCardProviderBinding
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.selector.ExchangeRateSelector
import io.muun.apollo.domain.selector.UserSelector
import io.muun.apollo.presentation.ui.new_operation.toRichText
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.common.model.ExchangeRateProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount
import kotlin.math.abs

class SecurityCardProviderFragment : Fragment() {

    companion object {
        private const val KEY_PROVIDER = "KEY_PROVIDER"

        fun newInstance(provider: SecurityCardProvider) = SecurityCardProviderFragment()
            .apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_PROVIDER, provider)
                }
            }
    }

    @Inject
    lateinit var viewModelFactory: SecurityCardProviderViewModel.Factory

    @Inject
    lateinit var userSel: UserSelector

    @Inject
    lateinit var exchangeRateSelector: ExchangeRateSelector

    @Inject
    lateinit var currencySelectionSharedViewModelFactory: CurrencySelectionSharedViewModel.Factory

    private val currencySelectionSharedViewModel: CurrencySelectionSharedViewModel by activityViewModels {
        CurrencySelectionSharedViewModelFactory(
            initialCurrentCurrency = CurrentCurrency.PRIMARY,
            assistedFactory = currencySelectionSharedViewModelFactory,
        )
    }

    private val viewModel: SecurityCardProviderViewModel by viewModels {
        SecurityCardProviderViewModelFactory(
            provider = requireNotNull(requireArguments().getParcelable(KEY_PROVIDER)),
            currencySelection = currencySelectionSharedViewModel.selectedCurrency.value,
            assistedFactory = viewModelFactory,
        )
    }

    private var _binding: FragmentSecurityCardProviderBinding? = null
    private val binding: FragmentSecurityCardProviderBinding
        get() = _binding as FragmentSecurityCardProviderBinding

    private val recyclerViewAdapter: SecurityCardProviderRecyclerViewAdapter by lazy {
        SecurityCardProviderRecyclerViewAdapter { securityCard ->
            val viewState =
                viewModel.viewState.value as? SecurityCardProviderViewModel.ViewState.Data ?: error(
                    "This shouldn't happen"
                )

            listener?.onSecurityCardClicked(
                provider = viewState.provider,
                securityCard = securityCard,
                footer = viewState.footer
            )
        }
    }

    private val snapHelper: SnapHelper by lazy {
        PagerSnapHelper().apply {
            attachToRecyclerView(binding.recyclerView)
        }
    }

    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getComponent().inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = FragmentSecurityCardProviderBinding.inflate(layoutInflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.setupRecyclerView()
        binding.setupFooter()

        viewLifecycleOwner.lifecycleScope.launch {
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
    }

    private fun FragmentSecurityCardProviderBinding.setupRecyclerView() {
        recyclerView.addItemDecoration(VerticalAspectPaddingDecoration())
        recyclerView.adapter = recyclerViewAdapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            private var lastSelectedPosition = RecyclerView.NO_POSITION

            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) {
                val centerY = recyclerView.height / 2f

                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)

                    val childCenterY = (child.top + child.bottom) / 2f
                    val distance = abs(centerY - childCenterY)

                    val maxDistance = recyclerView.height / 2f
                    val fraction = (distance / maxDistance).coerceIn(0f, 1f)

                    // Interpolate
                    val scale = 1f - fraction * (1f - 0.75f)
                    val alpha = 1f - fraction * (1f - 0.60f)

                    child.scaleX = scale
                    child.scaleY = scale
                    child.alpha = alpha
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                    val layoutManager = recyclerView.layoutManager ?: return
                    val snapView = snapHelper.findSnapView(layoutManager) ?: return

                    val adapterPosition = layoutManager.getPosition(snapView)

                    if (adapterPosition in recyclerViewAdapter.currentList.indices &&
                        adapterPosition != lastSelectedPosition
                    ) {
                        lastSelectedPosition = adapterPosition
                        viewModel.preselectProviderSecurityCard(cardIndex = adapterPosition)
                    }
                }
            }
        })
    }

    private fun FragmentSecurityCardProviderBinding.setupFooter() {
        viewFooter.setOnClickListener {
            currencySelectionSharedViewModel.rotateCurrencySelection()
        }
    }

    private fun handleViewState(viewState: SecurityCardProviderViewModel.ViewState) {
        if (viewState is SecurityCardProviderViewModel.ViewState.Data) {
            binding.handleData(viewState)
        }
    }

    private fun FragmentSecurityCardProviderBinding.handleData(data: SecurityCardProviderViewModel.ViewState.Data) {
        recyclerViewAdapter.submitList(data.provider.securityCards)

        val rateProvider = exchangeRateSelector.watchLatest()
            .toBlocking()
            .first()

        textViewFooterCardCost.text = getString(
            R.string.security_cards_marketplace_card_cost,
            requireActivity().toRichText(
                amt = when (data.footer.currentCurrency) {
                    CurrentCurrency.PRIMARY -> data.footer.cardCost.inCurrencyUnit(
                        rateProvider,
                        userSel.get().getPrimaryCurrency(rateProvider)
                    )
                    CurrentCurrency.BTC -> data.footer.cardCost.inBtc(rateProvider)
                },
                btcUnit = BitcoinUnit.BTC,
                isValid = true,
            )
        )
        textViewFooterShippingCost.text = getString(
            R.string.security_cards_marketplace_shipping_and_taxes_cost,
            requireActivity().toRichText(
                amt = when (data.footer.currentCurrency) {
                    CurrentCurrency.PRIMARY -> data.footer.shippingAndTaxesCost.inCurrencyUnit(
                        rateProvider,
                        userSel.get().getPrimaryCurrency(rateProvider)
                    )
                    CurrentCurrency.BTC -> data.footer.shippingAndTaxesCost.inBtc(
                        rateProvider
                    )
                },
                btcUnit = BitcoinUnit.BTC,
                isValid = true,
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        listener = null
    }

    interface Listener {
        fun onSecurityCardClicked(
            provider: SecurityCardProvider,
            securityCard: SecurityCard,
            footer: MarketplaceFooter,
        )
    }
}

/**
 * This RecyclerView.ItemDecoration is necessary to create the spacers needed for centering the
 * first and last card in the security cards carousel. As the cards size depends on device's screen
 * size (width), the vertical padding can't be hardcoded in the xml and need to be computed
 * programmatically, also we're using a decoration because changing the RecyclerView's padding mess
 * with the SnapHelper's behaviour.
 */
private class VerticalAspectPaddingDecoration private constructor(
    private val securityCardAspectRatio: Float,
    private val securityCardItemMargin: Float,
) : RecyclerView.ItemDecoration() {

    constructor() : this(
        // Corresponds to item_security_cards_marketplace_card.xml card aspect ratio
        securityCardAspectRatio = 0.632f,
        // Corresponds to item_security_cards_marketplace_card.xml card vertical margin
        securityCardItemMargin = 32f,
    )

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (parent.width == 0) return

        val itemHeight = (parent.width * securityCardAspectRatio) + securityCardItemMargin
        val verticalPadding = ((parent.height - itemHeight) / 2f).toInt().coerceAtLeast(0)

        val position = parent.getChildAdapterPosition(view)

        if (position == 0) {
            outRect.top = verticalPadding
        }

        if (position == state.itemCount - 1) {
            outRect.bottom = verticalPadding
        }
    }
}

// TODO: This should be moved into ViewModel (or even libwallet), the exchange rate window should be
// fixed for the whole flow so prices don't change while the user is navigating the marketplace
fun MonetaryAmount.inBtc(
    rateProvider: ExchangeRateProvider,
): MonetaryAmount = rateProvider.convert(this, BitcoinUnit.BTC.name)

fun MonetaryAmount.inCurrencyUnit(
    rateProvider: ExchangeRateProvider,
    currencyUnit: CurrencyUnit,
): MonetaryAmount = rateProvider.convert(this, currencyUnit)
