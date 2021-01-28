package io.muun.apollo.presentation.ui.settings.success_delete_wallet;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.common.Optional;

import android.content.Context;
import android.content.Intent;
import butterknife.BindView;
import butterknife.OnClick;

import javax.validation.constraints.NotNull;

public class SuccessDeleteWalletActivity extends BaseActivity<SuccessDeleteWalletPresenter> {

    @BindView(R.id.success_delete_wallet_description)
    HtmlTextView description;

    /**
     * Create an Intent to launch this Activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context,
                                                Optional<String> supportId) {

        return new Intent(context, SuccessDeleteWalletActivity.class)
                .putExtra(SuccessDeleteWalletView.SUPPORT_ID, supportId.orElse(null));
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.success_delete_wallet_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        setUpDescription();
    }

    private void setUpDescription() {
        final StyledStringRes styledExplanation = new StyledStringRes(
                getViewContext(),
                R.string.delete_wallet_success_description,
                presenter::navigateToFeedback
        );

        description.setText(styledExplanation.toCharSequence());
    }

    @OnClick(R.id.success_delete_wallet_action)
    public void onFinishClick() {
        presenter.navigateToLauncher();
    }

}
