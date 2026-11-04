package io.muun.apollo.presentation.ui.security_cards_marketplace

import android.animation.ArgbEvaluator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivitySecurityCardsMarketplaceBinding
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace
import io.muun.apollo.presentation.app.Navigator
import io.muun.apollo.presentation.ui.security_cards.utils.theme
import io.muun.apollo.presentation.ui.security_cards_country_picker.CountryPickerActivity
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter.CurrentCurrency
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getColorCompat
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class SecurityCardsMarketplaceActivity :
    AppCompatActivity(),
    SecurityCardProviderFragment.Listener {

    companion object {

        private const val EXTRA_INITIAL_COUNTRY_CODE = "initial_country_code"

        // Same height as mtrl_tabs_default_indicator, the default tab indicator
        private const val TAB_INDICATOR_HEIGHT_DP = 2f

        fun getIntent(context: Context, initialCountryInfo: CountryInfo): Intent {
            return Intent(context, SecurityCardsMarketplaceActivity::class.java)
                .putExtra(EXTRA_INITIAL_COUNTRY_CODE, initialCountryInfo.code.value)
        }
    }

    private val initialCountryCodeExtra: String
        get() = requireNotNull(intent.getStringExtra(EXTRA_INITIAL_COUNTRY_CODE))

    private val binding: ActivitySecurityCardsMarketplaceBinding by lazy {
        ActivitySecurityCardsMarketplaceBinding.inflate(layoutInflater)
    }

    @Inject
    lateinit var viewModelFactory: SecurityCardsMarketplaceViewModel.Factory

    @Inject
    lateinit var currencySelectionSharedViewModelFactory: CurrencySelectionSharedViewModel.Factory

    private val viewModel: SecurityCardsMarketplaceViewModel by viewModels {
        SecurityCardsMarketplaceViewModelFactory(
            initialCountryInfo = CountryInfo(MarketplaceCountryCode(initialCountryCodeExtra)),
            assistedFactory = viewModelFactory,
        )
    }

    private val currencySelectionSharedViewModel: CurrencySelectionSharedViewModel by viewModels {
        CurrencySelectionSharedViewModelFactory(
            initialCurrentCurrency = CurrentCurrency.PRIMARY,
            assistedFactory = currencySelectionSharedViewModelFactory,
        )
    }

    private val viewPagerAdapter: MarketplaceViewPagerAdapter by lazy {
        MarketplaceViewPagerAdapter(this)
    }

    private val SecurityCardMarketplace.SecurityCardProvider.primaryColor: Int
        @ColorInt get() = theme(this@SecurityCardsMarketplaceActivity).primaryColorHex

    // Replaces the default tab indicator (a StateListDrawable, whose color can't be mutated
    // in place) mirroring its shape: mtrl_tabs_default_indicator, a 2dp-high rectangle.
    private val tabIndicatorDrawable: GradientDrawable by lazy {
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setSize(-1, (resources.displayMetrics.density * TAB_INDICATOR_HEIGHT_DP).toInt())
            setColor(Color.TRANSPARENT)
        }
    }

    @Inject
    lateinit var navigator: Navigator

    private lateinit var countryPickerMenuItem: MenuItem

    private val countryPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val pickedCountry = CountryPickerActivity.getResult(requireNotNull(result.data))
                viewModel.changeCountry(newCountry = pickedCountry)
            }
        }

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
                    viewModel.viewEvent.collectLatest(::handleViewEvent)
                }
            }
        }

        setupHeader()
        setupViewPager()
        setupTabLayout()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_security_cards_marketplace, menu)

        countryPickerMenuItem = menu.findItem(R.id.country_picker)

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val countryPickerMenuItem = menu.findItem(R.id.country_picker)
        val country = when (val viewState = viewModel.viewState.value) {
            is SecurityCardsMarketplaceViewModel.ViewState.Loading -> viewState.country
            is SecurityCardsMarketplaceViewModel.ViewState.Data -> viewState.country
            is SecurityCardsMarketplaceViewModel.ViewState.NoData -> viewState.country
        }
        countryPickerMenuItem.title = country.flagEmoji
        countryPickerMenuItem.setOnMenuItemClickListener {
            navigator.navigateToCountryPickerForResult(
                this,
                country.code,
                countryPickerLauncher,
            )

            return@setOnMenuItemClickListener true
        }

        return super.onPrepareOptionsMenu(menu)
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
        binding.header.showTitle("Marketplace")
        binding.header.setTitleTextAppearance(R.style.MarketplaceToolbarTextAppearance)
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = viewPagerAdapter
    }

    private fun setupTabLayout() {
        binding.tabLayout.setSelectedTabIndicator(tabIndicatorDrawable)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setCustomView(R.layout.view_security_cards_marketplace_tab)
            tab.text = viewPagerAdapter.getPageName(position)
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

            private val argbEvaluator = ArgbEvaluator()

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                if (position >= viewPagerAdapter.itemCount) {
                    return
                }

                // TODO: This will change, dont review
                val fromColor = viewPagerAdapter.getProvider(position).primaryColor
                val toColor = if (position + 1 < viewPagerAdapter.itemCount) {
                    viewPagerAdapter.getProvider(position + 1).primaryColor
                } else {
                    fromColor
                }

                val color = argbEvaluator.evaluate(positionOffset, fromColor, toColor) as Int
                setTabIndicatorColor(color)
            }
        })
    }

    private fun handleViewState(viewState: SecurityCardsMarketplaceViewModel.ViewState) {
        when (viewState) {
            is SecurityCardsMarketplaceViewModel.ViewState.Loading -> Unit
            is SecurityCardsMarketplaceViewModel.ViewState.Data -> binding.handleData(viewState)
            is SecurityCardsMarketplaceViewModel.ViewState.NoData -> binding.handleNoData(viewState)
        }
    }

    private fun ActivitySecurityCardsMarketplaceBinding.handleData(
        data: SecurityCardsMarketplaceViewModel.ViewState.Data,
    ) {
        viewGroupNoData.visibility = View.INVISIBLE
        invalidateOptionsMenu()

        viewPagerAdapter.setData(data.providers)

        val currentProvider = data.providers[viewPager.currentItem.coerceIn(data.providers.indices)]
        setTabIndicatorColor(currentProvider.primaryColor)
        applyTabTextColors(data.providers)
    }

    /**
     * Makes each tab's text use its provider's primary color when selected. Must run after
     * TabLayoutMediator (re)populates the tabs, which recreates each tab's views. The single
     * ellipsized line is enforced by the tabs' custom view (see its layout for the rationale).
     */
    private fun ActivitySecurityCardsMarketplaceBinding.applyTabTextColors(
        providers: List<SecurityCardMarketplace.SecurityCardProvider>,
    ) {
        val defaultColor = getColorCompat(R.color.text_secondary_color)

        for (position in 0 until minOf(tabLayout.tabCount, providers.size)) {
            val tab = tabLayout.getTabAt(position) ?: continue

            val colors = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_selected),
                    intArrayOf(),
                ),
                intArrayOf(
                    providers[position].primaryColor,
                    defaultColor,
                ),
            )

            tab.view.children
                .filterIsInstance<TextView>()
                .forEach { it.setTextColor(colors) }
        }
    }

    /**
     * Tints the tab indicator by mutating [tabIndicatorDrawable]'s fill color directly.
     *
     * TabLayout.setSelectedTabIndicatorColor can't be used while the user is swiping: it triggers
     * a layout pass (updateTabViews -> setMinimumWidth -> requestLayout) and SlidingTabIndicator's
     * onLayout snaps the indicator to the selected tab, killing the swipe-follow animation. The
     * indicator drawable's inherent color is only honored while TabLayout's own indicator color is
     * TRANSPARENT (see app:tabIndicatorColor in the layout).
     */
    private fun setTabIndicatorColor(@ColorInt color: Int) {
        tabIndicatorDrawable.setColor(color)
    }

    private fun ActivitySecurityCardsMarketplaceBinding.handleNoData(
        data: SecurityCardsMarketplaceViewModel.ViewState.NoData,
    ) {
        viewGroupNoData.visibility = View.VISIBLE
        invalidateOptionsMenu()

        viewPagerAdapter.setData(emptyList())

        val styledRes = StyledStringRes(
            context = this@SecurityCardsMarketplaceActivity,
            resId = R.string.security_cards_marketplace_no_data_description,
            onLinkClick = this@SecurityCardsMarketplaceActivity::onNoDataContactUsClick
        )

        textViewNoDataDescription.movementMethod = LinkMovementMethod.getInstance()
        textViewNoDataDescription.text =
            styledRes.toCharSequence(data.country.name(this@SecurityCardsMarketplaceActivity))

        buttonNoDataSelectCountry.setOnClickListener {
            navigator.navigateToCountryPickerForResult(
                this@SecurityCardsMarketplaceActivity,
                data.country.code,
                countryPickerLauncher
            )
        }
    }

    private fun handleViewEvent(viewEvent: SecurityCardsMarketplaceViewModel.ViewEvent) {
        when (viewEvent) {
            is SecurityCardsMarketplaceViewModel.ViewEvent.NavigateToCardDetail -> {
                // TODO: make country part of the ViewState so every child inherits it
                val countryCode = when (val state = viewModel.viewState.value) {
                    is SecurityCardsMarketplaceViewModel.ViewState.Data -> state.country.code
                    is SecurityCardsMarketplaceViewModel.ViewState.NoData -> state.country.code
                    is SecurityCardsMarketplaceViewModel.ViewState.Loading -> state.country.code
                }

                navigator.navigateToCardDetail(
                    this,
                    countryCode,
                    viewEvent.providerUuid,
                    viewEvent.cardUuid,
                    currencySelectionSharedViewModel.selectedCurrency.value,
                )
            }
        }
    }

    private fun onNoDataContactUsClick(id: String) {
        navigator.navigateToSendGenericFeedback(this)
    }

    // region SecurityCardProviderFragment.Listener
    override fun onSecurityCardClicked(cardUuid: String) {
        viewModel.continueWithSecurityCard(cardUuid)
    }
    // endregion
}
