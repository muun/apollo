package io.muun.apollo.presentation.ui.single_action;


import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.view.MuunButton;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.OnClick;
import rx.functions.Action0;

import javax.validation.constraints.NotNull;

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

    @BindView(R.id.title)
    TextView title;

    @BindView(R.id.subtitle)
    TextView subtitle;

    @BindView(R.id.body)
    TextView body;

    @BindView(R.id.action_button)
    MuunButton actionButton;

    Action0 action;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.single_action_activity;
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
    }

    @OnClick(R.id.action_button)
    void onActionClick(View actionButton) {
        action.call();
    }

    private void setText(int titleResId, int subtitleResId, int bodyResId, int actionLabelResId) {
        title.setText(titleResId);
        subtitle.setText(subtitleResId);
        body.setText(bodyResId);
        actionButton.setText(actionLabelResId);
    }

    private void setAction(Action0 action) {
        this.action = action;
    }
}
