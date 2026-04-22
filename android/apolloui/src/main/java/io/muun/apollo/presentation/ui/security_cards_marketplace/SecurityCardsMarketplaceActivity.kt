package io.muun.apollo.presentation.ui.security_cards_marketplace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayoutMediator
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivitySecurityCardsMarketplaceBinding
import io.muun.apollo.presentation.app.Navigator
import io.muun.apollo.presentation.ui.security_cards_country_picker.CountryPickerActivity
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.MarketplaceFooter
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class SecurityCardsMarketplaceActivity : AppCompatActivity(), SecurityCardProviderFragment.Listener {

    companion object {

        private const val EXTRA_INITIAL_COUNTRY_INFO = "initial_country_info"

        fun getIntent(context: Context, initialCountryInfo: CountryInfo) =
            Intent(context, SecurityCardsMarketplaceActivity::class.java)
                .putExtra(EXTRA_INITIAL_COUNTRY_INFO, initialCountryInfo)
    }

    private val binding: ActivitySecurityCardsMarketplaceBinding by lazy {
        ActivitySecurityCardsMarketplaceBinding.inflate(layoutInflater)
    }

    @Inject
    lateinit var viewModelFactory: SecurityCardsMarketplaceViewModel.Factory

    private val viewModel: SecurityCardsMarketplaceViewModel by viewModels {
        SecurityCardsMarketplaceViewModelFactory(
            initialCountryInfo = requireNotNull(intent.getParcelableExtra(EXTRA_INITIAL_COUNTRY_INFO)),
            assistedFactory = viewModelFactory,
        )
    }

    private val viewPagerAdapter: MarketplaceViewPagerAdapter by lazy {
        MarketplaceViewPagerAdapter(this)
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
        val country =  when (val viewState = viewModel.viewState.value) {
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
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = viewPagerAdapter
    }

    private fun setupTabLayout() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = viewPagerAdapter.getPageName(position)
        }.attach()
    }

    private fun handleViewState(viewState: SecurityCardsMarketplaceViewModel.ViewState) {
        when (viewState) {
            is SecurityCardsMarketplaceViewModel.ViewState.Data -> binding.handleData(viewState)
            is SecurityCardsMarketplaceViewModel.ViewState.NoData -> binding.handleNoData(viewState)
        }
    }

    private fun ActivitySecurityCardsMarketplaceBinding.handleData(data: SecurityCardsMarketplaceViewModel.ViewState.Data) {
        viewGroupNoData.visibility = View.INVISIBLE
        invalidateOptionsMenu()

        viewPagerAdapter.setData(data.providers)
    }

    private fun ActivitySecurityCardsMarketplaceBinding.handleNoData(data: SecurityCardsMarketplaceViewModel.ViewState.NoData) {
        viewGroupNoData.visibility = View.VISIBLE
        invalidateOptionsMenu()

        val styledRes = StyledStringRes(
            context = this@SecurityCardsMarketplaceActivity,
            resId = R.string.security_cards_marketplace_no_data_description,
            onLinkClick = this@SecurityCardsMarketplaceActivity::onNoDataContactUsClick
        )

        textViewNoDataDescription.movementMethod = LinkMovementMethod.getInstance()
        textViewNoDataDescription.text = styledRes.toCharSequence(data.country.name)

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
            is SecurityCardsMarketplaceViewModel.ViewEvent.NavigateToCardDetail -> navigator.navigateToCardDetail(
                this,
                viewEvent.provider,
                viewEvent.securityCard,
                viewEvent.footer,
            )
        }
    }

    private fun onNoDataContactUsClick(id: String) {
        navigator.navigateToSendGenericFeedback(this)
    }

    // region SecurityCardProviderFragment.Listener
    override fun onSecurityCardClicked(
        provider: SecurityCardProvider,
        securityCard: SecurityCard,
        footer: MarketplaceFooter,
    ) {
        viewModel.continueWithSecurityCard(
            provider = provider,
            securityCard = securityCard,
            footer = footer,
        )
    }
    // endregion
}
