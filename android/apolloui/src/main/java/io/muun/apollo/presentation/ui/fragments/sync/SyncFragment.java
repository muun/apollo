package io.muun.apollo.presentation.ui.fragments.sync;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SignupSyncFragmentBinding;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.setup_pin_code.SetUpPinCodeActivity;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

public class SyncFragment extends SingleFragment<SyncPresenter> implements SyncView {

    private static final int REQUEST_PIN_CODE_SETUP = 5;

    private SignupSyncFragmentBinding binding() {
        return (SignupSyncFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return SignupSyncFragmentBinding::inflate;
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
        binding().initialSyncLoading.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void setIsExistingUser(boolean isExistingUser) {
        final String message = getString(
                isExistingUser ? R.string.signup_sync_loading : R.string.signup_sync_creating
        );
        binding().initialSyncLoading.setTitle(message);
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
