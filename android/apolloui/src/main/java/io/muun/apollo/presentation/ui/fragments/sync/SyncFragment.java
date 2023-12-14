package io.muun.apollo.presentation.ui.fragments.sync;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.setup_pin_code.SetUpPinCodeActivity;
import io.muun.apollo.presentation.ui.view.LoadingView;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import butterknife.BindString;
import butterknife.BindView;

public class SyncFragment extends SingleFragment<SyncPresenter> implements SyncView {

    private static final int REQUEST_PIN_CODE_SETUP = 5;

    @BindView(R.id.initial_sync_loading)
    LoadingView loadingView;

    @BindString(R.string.signup_sync_creating)
    String newUserMessage;

    @BindString(R.string.signup_sync_loading)
    String existingUserMessage;

    @Override
    protected int getLayoutResource() {
        return R.layout.signup_sync_fragment;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        hideKeyboard(view);
    }

    @Override
    public boolean onBackPressed() {
        return true;
    }

    @Override
    public void setLoading(boolean isLoading) {
        loadingView.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void setIsExistingUser(boolean isExistingUser) {
        loadingView.setTitle(isExistingUser ? existingUserMessage : newUserMessage);
    }

    @Override
    public void setUpPinCode(boolean canCancel) {
        final Intent intent = SetUpPinCodeActivity.getIntent(getActivity(), canCancel);

        requestExternalResult(REQUEST_PIN_CODE_SETUP, intent);
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {
        super.onExternalResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PIN_CODE_SETUP) {
            onPinCodeResult(resultCode);
        }
    }

    private void onPinCodeResult(int resultCode) {
        if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }

        presenter.pinCodeSetUpSuccess();
    }
}
