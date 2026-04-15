package io.muun.apollo.presentation.ui.security_cards_onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivitySecurityCardsOnboardingBinding
import io.muun.apollo.presentation.app.Navigator
import io.muun.apollo.presentation.ui.security_cards_country_picker.CountryPickerActivity
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class SecurityCardsOnboardingActivity :
    AppCompatActivity(),
    OnboardingCountrySelectorSlideFragment.Listener {

    companion object {
        fun getIntent(context: Context) =
            Intent(context, SecurityCardsOnboardingActivity::class.java)
    }

    private val binding: ActivitySecurityCardsOnboardingBinding by lazy {
        ActivitySecurityCardsOnboardingBinding.inflate(layoutInflater)
    }

    private val viewModel: SecurityCardsOnboardingViewModel by viewModels()
    private val countrySelectionViewModel: CountrySelectionSharedViewModel by viewModels()

    private val viewPagerAdapter: OnboardingViewPagerAdapter by lazy {
        OnboardingViewPagerAdapter(this)
    }

    private val countryPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val pickedCountry = CountryPickerActivity.getResult(requireNotNull(result.data))
                countrySelectionViewModel.onCountrySelected(countryInfo = pickedCountry)
            }
        }

    @Inject
    lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        setWindowInsetsCompat()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        getComponent().inject(this)

        setupHeader()
        setupViewPager()
        setupTabLayout()
        setupContinueButton()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collectLatest(::handleViewState)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                countrySelectionViewModel.selectedCountryInfo.collectLatest { selectedCountry ->
                    val isCountrySelected = selectedCountry != null
                    binding.buttonContinue.isEnabled = isCountrySelected
                }
            }
        }
    }

    private fun setupHeader() {
        binding.header.attachToActivity(this)
        binding.header.showTitle(R.string.security_cards_onboarding_title)
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = viewPagerAdapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val isFirstPage = position == 0
                val isLastPage = position == viewPagerAdapter.itemCount - 1
                binding.textViewFootnote.visibility = if (isFirstPage && !isLastPage) View.VISIBLE else View.INVISIBLE
                binding.buttonContinue.visibility = if (isLastPage) View.VISIBLE else View.INVISIBLE
            }
        })
    }

    private fun setupTabLayout() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()
    }

    private fun setupContinueButton() {
        binding.buttonContinue.isEnabled = false
        binding.buttonContinue.setOnClickListener {
            val selectedCountryInfo = requireNotNull(countrySelectionViewModel.selectedCountryInfo.value)
            navigator.navigateToSecurityCardsMarketplace(this, selectedCountryInfo)
        }
    }

    private fun handleViewState(viewState: SecurityCardsOnboardingViewModel.ViewState) {
        viewPagerAdapter.setData(viewState.slides)
    }

    // region SecurityCardsOnboardingLocationFragment.Listener
    override fun onCountryPickerClick() {
        navigator.navigateToCountryPickerForResult(
            this,
            countrySelectionViewModel.selectedCountryInfo.value?.code,
            countryPickerLauncher
        )
    }
    // endregion
}
