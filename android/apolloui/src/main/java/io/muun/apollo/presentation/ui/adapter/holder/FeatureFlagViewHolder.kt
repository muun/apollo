package io.muun.apollo.presentation.ui.adapter.holder

import android.view.View
import io.muun.apollo.databinding.ItemFeatureFlagBinding
import io.muun.apollo.presentation.ui.adapter.viewmodel.FeatureFlagViewModel

class FeatureFlagViewHolder(itemView: View) : BaseViewHolder<FeatureFlagViewModel>(itemView) {

    private val binding = ItemFeatureFlagBinding.bind(itemView)

    override fun bind(viewModel: FeatureFlagViewModel) {

        binding.featureFlagTitle.text = viewModel.overridableFeature.feature.toString()
        binding.featureFlagDescription.text = viewModel.overridableFeature.humanReadableDesc

        // Set initial state - switch is ON when feature is enabled (not overridden)
        binding.featureFlagSwitch.isChecked = viewModel.state == FeatureFlagViewModel.State.ENABLED

        // Clear any existing listener to prevent unwanted triggers
        binding.featureFlagSwitch.setOnCheckedChangeListener(null)
    }

    override fun onItemClick() {
        binding.featureFlagSwitch.isChecked = !binding.featureFlagSwitch.isChecked
    }
}