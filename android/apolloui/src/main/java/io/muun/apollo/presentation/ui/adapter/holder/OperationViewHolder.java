package io.muun.apollo.presentation.ui.adapter.holder;

import io.muun.apollo.databinding.HomeOperationsItemBinding;
import io.muun.apollo.presentation.model.UiOperation;
import io.muun.apollo.presentation.ui.adapter.viewmodel.OperationViewModel;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

public class OperationViewHolder extends BaseViewHolder<OperationViewModel> {

    private final HomeOperationsItemBinding binding;

    /**
     * View holder for the operation list items.
     */
    public OperationViewHolder(View view) {
        super(view);
        binding = HomeOperationsItemBinding.bind(view);
    }

    @Override
    public void bind(OperationViewModel viewModel) {
        final TextView title = binding.title;
        final Context context = title.getContext(); // any view will do
        final UiOperation op = viewModel.operation;

        title.setText(op.getFormattedTitle(context, true));

        binding.image.setPictureUri(op.getPictureUri(context));
        binding.time.setText(op.getFormattedDate());

        binding.amount.setText(op.getFormattedDisplayAmount(context));

        final CharSequence descriptionText;

        if (op.isCompleted()) {
            descriptionText = op.getFormattedDescription(context);

        } else {
            final CharSequence descriptionPrefix = op.getFormattedStatus(context);
            final CharSequence descriptionBody = op.getFormattedDescription(context);

            descriptionText = TextUtils.concat(descriptionPrefix, ": ", descriptionBody);
        }

        binding.description.setText(descriptionText);
    }
}
