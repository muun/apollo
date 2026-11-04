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
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.security_cards.utils.theme
import io.muun.apollo.presentation.ui.security_cards.utils.toCardCostRichText
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProviderParcelable
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.common.utils.BitcoinUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

class SecurityCardProviderFragment : Fragment() {

    companion object {
        private const val KEY_PROVIDER = "KEY_PROVIDER"

        fun newInstance(
            provider: SecurityCardMarketplace.SecurityCardProvider,
        ): SecurityCardProviderFragment {
            return SecurityCardProviderFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_PROVIDER, SecurityCardProviderParcelable(provider))
                }
            }
        }
    }

    private val providerArgument: SecurityCardMarketplace.SecurityCardProvider
        get() {
            val argument: SecurityCardProviderParcelable =
                requireNotNull(requireArguments().getParcelable(KEY_PROVIDER))

            return argument.provider
        }

    @Inject
    lateinit var viewModelFactory: SecurityCardProviderViewModel.Factory

    @Inject
    lateinit var currencySelectionSharedViewModelFactory: CurrencySelectionSharedViewModel.Factory

    private val currencySelectionSharedViewModel: CurrencySelectionSharedViewModel
        by activityViewModels {
            CurrencySelectionSharedViewModelFactory(
                initialCurrentCurrency = CurrentCurrency.PRIMARY,
                assistedFactory = currencySelectionSharedViewModelFactory,
            )
        }

    private val viewModel: SecurityCardProviderViewModel by viewModels {
        SecurityCardProviderViewModelFactory(
            provider = providerArgument,
            currencySelection = currencySelectionSharedViewModel.selectedCurrency.value,
            assistedFactory = viewModelFactory,
        )
    }

    private var _binding: FragmentSecurityCardProviderBinding? = null
    private val binding: FragmentSecurityCardProviderBinding
        get() = _binding as FragmentSecurityCardProviderBinding

    private val recyclerViewAdapter: SecurityCardProviderRecyclerViewAdapter by lazy {
        SecurityCardProviderRecyclerViewAdapter(
            providerPrimaryColor = providerArgument.theme(requireContext()).primaryColorHex,
        ) { securityCard ->
            // Navigation waits for the tapped card to snap to the center: the upcoming hero
            // transition to card detail must start from the centered position.
            binding.scrollToSecurityCardThenNavigate(securityCard)
        }
    }

    private val snapHelper = PagerSnapHelper()

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
        snapHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

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

    private fun FragmentSecurityCardProviderBinding.handleData(
        data: SecurityCardProviderViewModel.ViewState.Data,
    ) {
        recyclerViewAdapter.submitList(data.provider.securityCards)

        footerDivider.dividerColor = data.provider.theme(requireContext()).primaryColorHex

        val cardCost = when (data.footer.currentCurrency) {
            CurrentCurrency.PRIMARY -> data.footer.cardCost
            CurrentCurrency.BTC -> BitcoinUtils.satoshisToBitcoins(data.footer.cardCostInSats)
        }
        textViewFooterCardCost.text = cardCost.toCardCostRichText(requireContext())

        val shippingCost = when (data.footer.currentCurrency) {
            CurrentCurrency.PRIMARY -> data.footer.shippingAndTaxesCost
            CurrentCurrency.BTC ->
                BitcoinUtils.satoshisToBitcoins(data.footer.shippingAndTaxesCostInSats)
        }
        textViewFooterShippingCost.text = getString(
            R.string.security_cards_marketplace_shipping_and_taxes_cost,
            MoneyHelper.formatLongMonetaryAmount(
                shippingCost,
                BitcoinUnit.BTC,
                requireContext().locale(),
            ),
        )
    }

    private fun FragmentSecurityCardProviderBinding.scrollToSecurityCardThenNavigate(
        securityCard: SecurityCardMarketplace.SecurityCard,
    ) {
        val position = recyclerViewAdapter.currentList.indexOf(securityCard)
        val cardView =
            requireNotNull(recyclerView.findViewHolderForAdapterPosition(position)).itemView

        recyclerView.smoothScrollToCard(snapHelper, cardView) {
            listener?.onSecurityCardClicked(securityCard.uuid)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        listener = null
    }

    interface Listener {
        fun onSecurityCardClicked(cardUuid: String)
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

/**
 * Smoothly scrolls [cardView] to [snapHelper]'s snap position (the carousel center) and invokes
 * [onScrollToCardFinish] once it settles there. Fires immediately if the card is already snapped.
 * A user drag cancels the pending callback, and a new call replaces any previous pending one
 * (last tap wins).
 *
 * The in-flight scroll listener is tracked through the RecyclerView's plain [View.setTag]: a keyed
 * tag can't use a runtime-generated key ([View.setTag] requires an application resource id), so
 * this extension is private to the one fragment whose carousel's tag is known to be free.
 */
private fun RecyclerView.smoothScrollToCard(
    snapHelper: SnapHelper,
    cardView: View,
    onScrollToCardFinish: () -> Unit,
) {
    val layoutManager = layoutManager ?: return
    val snapDistance = snapHelper.calculateDistanceToFinalSnap(layoutManager, cardView)

    val dx = snapDistance?.get(0) ?: 0
    val dy = snapDistance?.get(1) ?: 0

    (tag as? RecyclerView.OnScrollListener)?.let(::removeOnScrollListener)
    tag = null

    if (dx == 0 && dy == 0) {
        onScrollToCardFinish()
        return
    }

    val scrollListener = object : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                // The user grabbed the carousel: the card won't settle where we aimed.
                RecyclerView.SCROLL_STATE_DRAGGING -> stopListening(recyclerView)

                RecyclerView.SCROLL_STATE_IDLE -> {
                    stopListening(recyclerView)
                    onScrollToCardFinish()
                }

                else -> Unit
            }
        }

        private fun stopListening(recyclerView: RecyclerView) {
            recyclerView.removeOnScrollListener(this)
            recyclerView.tag = null
        }
    }

    tag = scrollListener
    addOnScrollListener(scrollListener)
    smoothScrollBy(dx, dy)
}
