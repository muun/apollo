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
import io.muun.apollo.databinding.ActivitySecurityCardsFullSpecsBinding
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.AdditionalInfo
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCard
import io.muun.apollo.presentation.ui.security_cards_marketplace.models.SecurityCardProvider
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import io.muun.apollo.presentation.ui.view.MuunHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class SecurityCardsFullSpecsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_CARD = "card"

        fun getIntent(context: Context, card: SecurityCard): Intent =
            Intent(context, SecurityCardsFullSpecsActivity::class.java)
                .putExtra(EXTRA_CARD, card)
    }

    private val binding: ActivitySecurityCardsFullSpecsBinding by lazy {
        ActivitySecurityCardsFullSpecsBinding.inflate(layoutInflater)
    }

    @Inject
    lateinit var viewModelFactory: SecurityCardsFullSpecsViewModel.Factory

    private val viewModel: SecurityCardsFullSpecsViewModel by viewModels {
        SecurityCardsFullSpecsViewModelFactory(
            card = requireNotNull(intent.getParcelableExtra(EXTRA_CARD)),
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
        binding.recyclerView.addItemDecoration(SpecSectionItemDecoration(this))
    }

    private fun handleViewState(viewState: SecurityCardsFullSpecsViewModel.ViewState) {
        adapter.submitList(viewState.items)
    }

    private fun showAdditionalInfoBottomSheet(info: AdditionalInfo) {
        SpecAdditionalInfoBottomSheetDialogFragment.newInstance(info)
            .show(supportFragmentManager, SpecAdditionalInfoBottomSheetDialogFragment::class.java.simpleName)
    }
}
