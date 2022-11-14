package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.errors.BugDetected;
import io.muun.apollo.presentation.app.di.ApplicationComponent;
import io.muun.apollo.presentation.ui.activity.extension.ExternalResultExtension.Caller;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.activity.extension.PermissionManagerExtension.PermissionRequester;
import io.muun.apollo.presentation.ui.base.di.FragmentComponent;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.Optional;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import icepick.Icepick;
import rx.functions.Action0;
import rx.functions.Action1;
import timber.log.Timber;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public abstract class BaseFragment<PresenterT extends Presenter> extends Fragment
        implements BaseView, Caller, PermissionRequester {

    private FragmentComponent component;

    private Unbinder butterKnifeUnbinder;

    @Inject
    protected PresenterT presenter;

    @Inject
    protected ApplicationLockManager lockManager;

    protected abstract void inject();

    /**
     * Returns the id of the resource with the layout of the fragment.
     */
    @LayoutRes
    protected abstract int getLayoutResource();

    /**
     * Returns the id of the resource with the menu for this activity.
     */
    @MenuRes
    protected int getMenuResource() {
        return 0;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // report that fragment wants do populate the options menu
        setHasOptionsMenu(getMenuResource() != 0);

        Icepick.restoreInstanceState(this, savedInstanceState);

        initializePresenter(savedInstanceState);
    }

    @CallSuper
    protected void initializePresenter(@Nullable Bundle savedInstanceState) {
        inject();
        setPresenterView();
        presenter.restoreState(savedInstanceState);
    }

    @SuppressWarnings("unchecked")
    private void setPresenterView() {
        presenter.setView(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        final int menuRes = getMenuResource();

        if (menuRes != 0) {
            inflater.inflate(menuRes, menu);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Nullable
    @Override
    @CallSuper
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(getLayoutResource(), container, false);
        butterKnifeUnbinder = ButterKnife.bind(this, view);
        initializeUi(view);
        return view;
    }

    /**
     * Override this method to add any activity initialization logic.
     *
     * @param view the View for the fragment's UI as returned by {@link #onCreateView})
     */
    protected void initializeUi(View view) {
    }

    /**
     * Override this method to add any clean up logic.
     */
    protected void tearDownUi() {
        presenter.destroy();
    }

    @NotNull
    @Override
    public Bundle getArgumentsBundle() {
        final Bundle arguments = getArguments();
        return arguments != null ? new Bundle(arguments) : new Bundle();
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        presenter.setUp(getArgumentsBundle());
        presenter.afterSetUp();
    }

    @Override
    @CallSuper
    public void onPause() {
        super.onPause();
        presenter.tearDown();
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
        presenter.saveState(outState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        presenter.onViewCreated(savedInstanceState);
    }

    @Override
    @CallSuper
    public void onDestroyView() {
        butterKnifeUnbinder.unbind();
        tearDownUi();
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();

        if (blockScreenshots()) {
            // NOTE:
            // We block screenshots between onStart/onDetach (rather than onAttach/onDetach). Why?

            // Normally, onAttach will be called after onCreate has completed (during onStart,
            // in fact). However, if the Activity was killed by the system and is being recreated,
            // the call will come during onCreate.

            // Since we set up our extensions in onCreate (notably after calling super.onCreate),
            // ExtensibleActivity won't have the chance to initialize before Fragments receive the
            // onAttach call, and screenshotBlockExtension will be null.

            // This is not theoretical, we've seen this in the wild.
            final String caller = this.getClass().getSimpleName();
            getParentActivity().screenshotBlockExtension.startBlockingScreenshots(caller);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (blockScreenshots()) {
            final String caller = this.getClass().getSimpleName();
            getParentActivity().screenshotBlockExtension.stopBlockingScreenshots(caller);
        }
    }

    @Override
    @NotNull
    public Context getViewContext() {
        return getActivity();
    }

    @Deprecated // Use Fragment#requireContext() instead. Keeping to avoid re-use.
    @Nullable
    @Override
    public Context getContext() {
        return super.getContext();
    }

    /**
     * Safely try and show a simple test Toast. You should not call this method from any thread
     * other than Android's MainThread. It's a coding error. We're just adding a safety measure to
     * avoid fatal crashes in weird/edge situations.
     */
    @Override
    @MainThread
    public void showTextToast(String text) {

        final Runnable showToast =
                () -> Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();

        // Shouldn't happen but we're adding this to prevent weird crashes.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showToast.run();

        } else {
            Timber.e(new BugDetected("Attempted to show toast from background thread"));

            // Force the toast to appear on the main thread, the toast message should always be
            // shown to the user, in case it is a relevant error message.
            if (getActivity() != null && !getActivity().isFinishing()) {
                getActivity().runOnUiThread(showToast);
            }
        }
    }

    @Override
    public void showPlayServicesDialog(Action1<Activity> showDialog) {
        showDialog.call(getParentActivity());
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    @Override
    public void showErrorDialog(@StringRes int resId) {
        showErrorDialog(resId, null);
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    @Override
    public void showErrorDialog(@StringRes int resId, Action0 followupAction) {
        getParentActivity().showErrorDialog(resId, followupAction);
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    @Override
    public void showErrorDialog(CharSequence errorMsg) {
        showErrorDialog(errorMsg, null, null);
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    @Override
    public void showErrorDialog(CharSequence errorMsg, Action0 followupAction) {
        getParentActivity().showErrorDialog(errorMsg, followupAction);
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    @Override
    public void showErrorDialog(CharSequence errorMsg, Action0 followup, Action0 onDismiss) {
        getParentActivity().showErrorDialog(errorMsg, followup, onDismiss);
    }

    @Override
    public void showDialog(MuunDialog dialog) {
        getParentActivity().showDialog(dialog);
    }

    @Override
    public void dismissDialog() {
        getParentActivity().dismissDialog();
    }

    @Override
    public void finishActivity() {
        getParentActivity().finishActivity();
    }

    @NotNull
    protected FragmentComponent getComponent() {

        if (component == null) {
            component = getApplicationComponent().fragmentComponent();
        }

        return component;
    }

    @NotNull
    private ApplicationComponent getApplicationComponent() {
        return ((BaseActivity) getActivity()).getApplicationComponent();
    }

    /**
     * Determine whether you have been granted some permissions.
     */
    protected final boolean allPermissionsGranted(String... permissions) {
        return getParentActivity().allPermissionsGranted(permissions);
    }

    /**
     * Request some permissions to be granted to this application.
     */
    protected final void requestPermissions(String... permissions) {
        getParentActivity().requestPermissions(this, permissions);
    }

    /**
     * Gets whether you can show UI with rationale for requesting a permission. Return false if the
     * permission was denied with the 'Never ask again' checkbox checked. See: {@link
     * PermissionManagerExtension#canShowRequestPermissionRationale(String)}
     */
    public final boolean canShowRequestPermissionRationale(String permission) {
        return getParentActivity().canShowRequestPermissionRationale(permission);
    }

    /**
     * Override this method to be notified when ALL requested permissions have been granted.
     */
    public void onPermissionsGranted(String[] grantedPermissions) {
        // Do nothing
    }

    /**
     * Override this method to be notified when SOME of the requested permissions have been denied.
     */
    public void onPermissionsDenied(String[] deniedPermissions) {
        // Do nothing
    }

    protected void hideKeyboard(View view) {
        UiUtils.tryHideKeyboard(requireContext(), view);

        UiUtils.lastResortHideKeyboard(getActivity());
    }

    protected final void showDrawerDialog(DialogFragment dialog) {
        getParentActivity().showDrawerDialog(dialog);
    }

    protected final void requestExternalResult(int requestCode, Intent intent) {
        getParentActivity().requestExternalResult(this, requestCode, intent);
    }

    protected final String requestExternalResult(int requestCode, DialogFragment dialog) {
        return getParentActivity().requestExternalResult(this, requestCode, dialog);
    }

    protected final void requestDelegatedExternalResult(int requestCode, Intent intent) {
        getParentActivity().requestDelegatedExternalResult(requestCode, intent);
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {

    }

    protected void setVisible(View view, boolean isVisible) {
        view.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected BaseActivity getParentActivity() {
        return (BaseActivity) getActivity();
    }

    protected Optional<BaseActivity> safeGetParentActivity() {
        return Optional.ofNullable(getParentActivity());
    }

    protected boolean blockScreenshots() {
        return false;
    }

    @Override
    public int getMId() {
        return getId();
    }
}
