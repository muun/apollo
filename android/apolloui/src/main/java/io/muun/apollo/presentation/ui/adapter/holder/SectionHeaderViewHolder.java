package io.muun.apollo.presentation.ui.adapter.holder;

import io.muun.apollo.databinding.VItemSectionHeaderBinding;
import io.muun.apollo.presentation.ui.adapter.viewmodel.SectionHeaderViewModel;

import android.view.View;

public class SectionHeaderViewHolder extends BaseViewHolder<SectionHeaderViewModel> {

    private final VItemSectionHeaderBinding binding;

    public SectionHeaderViewHolder(View view) {
        super(view);
        binding = VItemSectionHeaderBinding.bind(view);
    }

    @Override
    public void bind(SectionHeaderViewModel viewModel) {
        binding.divider.setVisibility(viewModel.model.showDivider ? View.VISIBLE : View.GONE);
        binding.label.setText(viewModel.model.title);
    }
}
