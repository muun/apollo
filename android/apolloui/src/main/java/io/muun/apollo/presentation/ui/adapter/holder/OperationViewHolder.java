package io.muun.apollo.presentation.ui.adapter.holder;

import io.muun.apollo.R;
import io.muun.apollo.presentation.model.UiOperation;
import io.muun.apollo.presentation.ui.adapter.viewmodel.OperationViewModel;
import io.muun.apollo.presentation.ui.view.ProfilePictureView;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import butterknife.BindView;

public class OperationViewHolder extends BaseViewHolder<OperationViewModel> {

    @BindView(R.id.home_operations_item_profile_picture)
    protected ProfilePictureView image;

    @BindView(R.id.home_operations_item_title)
    protected TextView title;

    @BindView(R.id.home_operations_item_text_amount)
    protected TextView amount;

    @BindView(R.id.home_operations_item_text_description)
    protected TextView description;

    @BindView(R.id.home_operations_item_text_time)
    protected TextView time;

    /**
     * View holder for the operation list items.
     */
    public OperationViewHolder(View view) {
        super(view);
    }

    @Override
    public void bind(OperationViewModel viewModel) {
        final Context context = title.getContext(); // any view will do
        final UiOperation op = viewModel.operation;

        title.setText(op.getFormattedTitle(context, true));

        image.setPictureUri(op.getPictureUri(context));
        time.setText(op.getFormattedDate());

        amount.setText(op.getFormattedDisplayAmount(context));

        final CharSequence descriptionText;

        if (op.isCompleted()) {
            descriptionText = op.getFormattedDescription(context);

        } else {
            final CharSequence descriptionPrefix = op.getFormattedStatus(context);
            final CharSequence descriptionBody = op.getFormattedDescription(context);

            descriptionText = TextUtils.concat(descriptionPrefix, ": ", descriptionBody);
        }

        description.setText(descriptionText);
    }
}
