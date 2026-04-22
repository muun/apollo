package io.muun.apollo.presentation.ui.security_cards_country_picker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import io.muun.apollo.R
import io.muun.apollo.databinding.ActivityCountryPickerBinding
import io.muun.apollo.presentation.ui.security_cards_country_picker.models.CountryInfo
import io.muun.apollo.presentation.ui.utils.getComponent
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import io.muun.apollo.presentation.ui.view.MuunHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class CountryPickerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SELECTED_COUNTRY_CODE = "selected_country_code"
        private const val RESULT_COUNTRY = "result_country"

        fun getIntent(context: Context, selectedCountryCode: String?): Intent =
            Intent(context, CountryPickerActivity::class.java).apply {
                putExtra(EXTRA_SELECTED_COUNTRY_CODE, selectedCountryCode)
            }

        fun getResult(data: Intent): CountryInfo =
            requireNotNull(data.getParcelableExtra(RESULT_COUNTRY))
    }

    private val binding: ActivityCountryPickerBinding by lazy {
        ActivityCountryPickerBinding.inflate(layoutInflater)
    }

    @Inject
    lateinit var viewModelFactory: CountryPickerViewModel.Factory

    private val viewModel: CountryPickerViewModel by viewModels {
        CountryPickerViewModelFactory(
            selectedCountryCode = intent.getStringExtra(EXTRA_SELECTED_COUNTRY_CODE),
            assistedFactory = viewModelFactory,
        )
    }

    private val recyclerViewAdapter: CountryPickerRecyclerViewAdapter by lazy {
        CountryPickerRecyclerViewAdapter(onCountryClick = ::onCountrySelected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setWindowInsetsCompat()
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        getComponent().inject(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collectLatest(::handleViewState)
            }
        }

        setupHeader()
        setupRecyclerView()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_country_picker, menu)

        val searchView = menu.findItem(R.id.search).actionView as SearchView
        searchView.queryHint = getString(R.string.security_cards_country_picker_search_hint)
        searchView.maxWidth = Int.MAX_VALUE

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String) = false
            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.setQuery(newText)
                return true
            }
        })

        searchView.setOnCloseListener {
            viewModel.setQuery("")
            false
        }

        return true
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
        binding.header.showTitle(R.string.security_cards_country_picker_title)
        binding.header.setNavigation(MuunHeader.Navigation.BACK)
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = recyclerViewAdapter
    }

    private fun handleViewState(viewState: CountryPickerViewModel.ViewState) {
        when (viewState) {
            is CountryPickerViewModel.ViewState.Data -> {
                binding.recyclerView.visibility = View.VISIBLE
                binding.textViewEmptyState.visibility = View.GONE
                recyclerViewAdapter.submitList(viewState.countries)
            }
            is CountryPickerViewModel.ViewState.NoData -> {
                binding.recyclerView.visibility = View.GONE
                binding.textViewEmptyState.visibility = View.VISIBLE
                binding.textViewEmptyState.text =
                    getString(R.string.security_cards_country_picker_no_results, viewState.query)
            }
        }
    }

    private fun onCountrySelected(selection: CountryInfo) {
        val resultIntent = Intent().apply {
            putExtra(RESULT_COUNTRY, selection)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
