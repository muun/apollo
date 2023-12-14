package io.muun.apollo.presentation.ui.single_action;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.need_recovery_code.NeedRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import javax.validation.constraints.NotNull;

public class V2SingleActionActivity extends SingleFragmentActivity<V2SingleActionPresenter> {

    public static final String ACTION_TYPE = "action_type";
    public static final String ACTION_ARGS = "action_args";

    public enum ActionType {
        REQUEST_RECOVERY_CODE_SETUP,
        SHOW_PREPARE_OPERATION_ERROR
    }

    /**
     * Get an Intent to launch an action action fragment, with arguments.
     */
    public static Intent getIntent(@NotNull Context context,
                                   @NotNull ActionType actionType,
                                   @NotNull Bundle arguments) {

        return new Intent(context, V2SingleActionActivity.class)
                .putExtra(ACTION_TYPE, actionType.name())
                .putExtra(ACTION_ARGS, arguments);
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.single_action_activity_v2;
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.fragment_container;
    }

    @Override
    public MuunHeader getHeader() {
        // this activity has no header/toolbar. Should it?
        // TODO add a toolbar (and hide it) or handle it better
        return null;
    }

    @Override
    protected SingleFragment getInitialFragment() {
        final SingleFragment fragment = createActionFragment();
        final Bundle arguments = getArgumentsBundle().getBundle(ACTION_ARGS);

        Preconditions.checkState(arguments != null);

        fragment.setArguments(arguments);
        return fragment;
    }

    private SingleFragment createActionFragment() {
        final String actionTypeName = getArgumentsBundle().getString(ACTION_TYPE);

        Preconditions.checkState(actionTypeName != null);

        final ActionType type = ActionType.valueOf(actionTypeName);

        switch (type) {
            case REQUEST_RECOVERY_CODE_SETUP:
                return new NeedRecoveryCodeFragment();

            case SHOW_PREPARE_OPERATION_ERROR:
                return new NewOperationErrorFragment();

            default:
                throw new MissingCaseError(type);
        }
    }
}
