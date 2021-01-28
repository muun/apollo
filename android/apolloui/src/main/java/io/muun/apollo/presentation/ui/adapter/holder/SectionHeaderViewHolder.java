package io.muun.apollo.presentation.ui.adapter.holder;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.adapter.viewmodel.SectionHeaderViewModel;

import android.view.View;
import android.widget.TextView;
import butterknife.BindView;

public class SectionHeaderViewHolder extends BaseViewHolder<SectionHeaderViewModel> {

    @BindView(R.id.section_header_item_divider)
    View divider;

    @BindView(R.id.section_header_item_label)
    TextView label;

    public SectionHeaderViewHolder(View view) {
        super(view);
    }

    @Override
    public void bind(SectionHeaderViewModel viewModel) {
        divider.setVisibility(viewModel.model.showDivider ? View.VISIBLE : View.GONE);
        label.setText(viewModel.model.title);
    }
}
