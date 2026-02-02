package io.muun.apollo.presentation.ui.fragments.need_recovery_code;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.fragments.single_action.SingleActionFragment;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.utils.Preconditions;

public class NeedRecoveryCodeFragment extends SingleActionFragment<NeedRecoveryCodePresenter> {

    public static final String FLOW = "flow";

    public enum Flow {
        CHANGE_PASSWORD
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected String getTitle() {
        return getString(R.string.recovery_code_missing_title);
    }

    @Override
    protected CharSequence getDescription() {
        final String flowName = getArgumentsBundle().getString(FLOW);

        Preconditions.checkState(flowName != null);

        final Flow flow = Flow.valueOf(flowName);

        if (flow == Flow.CHANGE_PASSWORD) {
            return getString(R.string.recovery_code_missing_message);
        }

        throw new MissingCaseError(flow);
    }

    @Override
    protected int getActionLabelRes() {
        return R.string.recovery_code_missing_action;
    }

    @Override
    protected int getImageRes() {
        return R.drawable.missing_recovery;
    }

    @Override
    protected int getImageWidth() {
        return UiUtils.dpToPx(requireContext(), 234);
    }

    @Override
    protected int getImageHeight() {
        return UiUtils.dpToPx(requireContext(), 106);
    }

    @Override
    protected void onActionClick() {
        presenter.goToRecoveryCodeSetup();
    }

}
