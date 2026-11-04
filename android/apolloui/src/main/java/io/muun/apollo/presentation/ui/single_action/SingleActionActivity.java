package io.muun.apollo.presentation.ui.single_action;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SingleActionActivityBinding;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.base.BaseView;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;
import rx.functions.Action0;

import jakarta.validation.constraints.NotNull;

public class SingleActionActivity extends BaseActivity<SingleActionPresenter>
        implements BaseView {

    public static final String ACTION_TYPE = "action_type";

    public enum ActionType {
        REQUEST_UPDATE,
        REQUEST_RESTART
    }

    /**
     * Creates an intent to launch SingleActionActivity.
     */
    public static Intent getIntent(@NotNull Context context, @NotNull ActionType actionType) {

        final Intent intent = new Intent(context, SingleActionActivity.class);
        intent.putExtra(ACTION_TYPE, actionType.name());

        return intent;
    }

    private SingleActionActivityBinding binding() {
        return (SingleActionActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return SingleActionActivityBinding::inflate;
    }

    Action0 action;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        final ActionType actionType = ActionType.valueOf(
                getArgumentsBundle().getString(ACTION_TYPE)
        );

        presenter.reportActionType(actionType);

        switch (actionType) {

            case REQUEST_UPDATE:
                setAction(presenter::requestUpdateAction);
                setText(
                        R.string.request_update_title,
                        R.string.request_update_subtitle,
                        R.string.request_update_body,
                        R.string.request_update_action
                );
                break;

            case REQUEST_RESTART:
                setAction(presenter::requestRestartAction);
                setText(
                        R.string.request_restart_title,
                        R.string.request_restart_subtitle,
                        R.string.request_restart_body,
                        R.string.request_restart_action
                );
                break;

            default:
                break;
        }

        binding().actionButton.setOnClickListener(v -> action.call());
    }

    private void setText(int titleResId, int subtitleResId, int bodyResId, int actionLabelResId) {
        binding().title.setText(titleResId);
        binding().subtitle.setText(subtitleResId);
        binding().body.setText(bodyResId);
        binding().actionButton.setText(actionLabelResId);
    }

    private void setAction(Action0 action) {
        this.action = action;
    }
}
