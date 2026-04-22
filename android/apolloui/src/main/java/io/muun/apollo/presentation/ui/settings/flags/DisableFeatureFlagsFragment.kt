package io.muun.apollo.presentation.ui.settings.flags

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentDisableFeatureFlagsBinding
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.presentation.ui.adapter.ItemAdapter
import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory
import io.muun.apollo.presentation.ui.adapter.viewmodel.FeatureFlagViewModel
import io.muun.apollo.presentation.ui.adapter.viewmodel.ItemViewModel
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunHeader

class DisableFeatureFlagsFragment : SingleFragment<DisableFeatureFlagsPresenter>(),
    DisableFeatureFlagsView {

    private val binding: FragmentDisableFeatureFlagsBinding
        get() = getBinding() as FragmentDisableFeatureFlagsBinding

    private lateinit var adapter: ItemAdapter

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.fragment_disable_feature_flags

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentDisableFeatureFlagsBinding::inflate
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            showTitle(R.string.settings_disable_feature_flags)
            setNavigation(MuunHeader.Navigation.BACK)
        }
    }

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        adapter = ItemAdapter(ViewHolderFactory())
        adapter.setOnItemClickListener { viewModel: ItemViewModel? ->
            this.onItemClick(viewModel)
        }

        binding.featureFlagsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DisableFeatureFlagsFragment.adapter
        }
    }

    override fun setState(
        features: List<MuunFeature.OverridableFeature.Overridable>,
        featureOverrides: List<MuunFeature.OverridableFeature.Overridable>
    ) {

        val featureFlagViewModels = features
            .map { feature ->
                val state = if (featureOverrides.contains(feature)) {
                    FeatureFlagViewModel.State.DISABLED
                } else {
                    FeatureFlagViewModel.State.ENABLED
                }
                FeatureFlagViewModel(feature, state)
            }

        adapter.setItems(featureFlagViewModels)
    }

    private fun onItemClick(viewModel: ItemViewModel?) {
        if (viewModel is FeatureFlagViewModel) {
            presenter.toggleFeatureFlag(viewModel.overridableFeature, viewModel.state)
        }
    }
}