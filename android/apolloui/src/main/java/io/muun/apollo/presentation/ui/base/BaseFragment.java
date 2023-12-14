package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.errors.BugDetected;
import io.muun.apollo.presentation.app.di.ApplicationComponent;
import io.muun.apollo.presentation.ui.activity.extension.ExternalResultExtension.Caller;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.activity.extension.PermissionManagerExtension;
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
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.viewbinding.ViewBinding;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import icepick.Icepick;
import kotlin.jvm.functions.Function3;
import rx.functions.Action0;
import rx.functions.Action1;
import timber.log.Timber;

import java.util.Objects;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public abstract class BaseFragment<PresenterT extends Presenter> extends Fragment
        implements BaseView, Caller, PermissionRequester, DefaultLifecycleObserver {

    private FragmentComponent component;

    private Unbinder butterKnifeUnbinder;

    /**
     * Reserved property for View Binding use, will be automatically cleaned up by this parent class
     * in {@link #onDestroyView()}.
     * TODO: this should be a protected var + private set when we kotlinize this mofo
     */
    private ViewBinding _binding;

    protected ViewBinding getBinding() {
        return _binding;
    }

    @Inject
    protected PresenterT presenter;

    @Inject
    protected ApplicationLockManager lockManager;

    protected abstract void inject();

    /**
     * Returns the id of the resource with the layout of the fragment.
     * TODO rm this once all fragments have successfully migrated from butterKnife to view binding.
     */
    @LayoutRes
    protected abstract int getLayoutResource();

    /**
     * Override this method to opt in to use View Binding. This will turn into an abstract method
     * to force all subclasses to implement once we finish the transition from ButterKnife to
     * ViewBinding. For now, we leave this default impl to not bother classes that haven't
     * transitioned yet.
     */
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return null;
    }

    /**
     * Returns the id of the resource with the menu for this activity.
     */
    @MenuRes
    protected int getMenuResource() {
        return 0;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onAttach");

        safeGetParentActivity()
                .map(BaseActivity::getLifecycle)
                .ifPresent(lifecycle -> lifecycle.addObserver(this));
    }

    /**
     * Notifies that {@code ON_CREATE} event occurred for parent activity. Note this is the
     * new, recommended way of handling deprecated Fragment#onActivityCreated callback.
     *
     * <p>Why are we removing the lifecycle observer in the onCreate method? Shouldn't we detach the
     * lifecycle in the onDestroy?</p>
     *
     * <p><a href="https://developer.android.com/jetpack/androidx/releases/fragment#1.3.0-alpha02">
     * As per the changelog:</a></p>
     *
     * <p>The onActivityCreated() method is now deprecated. Code touching the fragment's view should
     * be done in onViewCreated() (which is called immediately before onActivityCreated()) and other
     * initialization code should be in onCreate(). To receive a callback specifically when the
     * activity's onCreate() is complete, a LifeCycleObserver should be registered on the activity's
     * Lifecycle in onAttach(), and removed once the onCreate() callback is received.</p>
     *
     * @param owner the component, whose state was changed
     */
    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        safeGetParentActivity()
                .map(BaseActivity::getLifecycle)
                .ifPresent(lifecycle -> lifecycle.removeObserver(this));

        Timber.d("Lifecycle: " + owner.getClass().getSimpleName() + "#onCreate");
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onActivityCreated");

        onActivityCreated();
    }

    /**
     * See {@link Fragment#onCreate(Bundle)}.
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     */
    @Override
    @CallSuper
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onCreate");

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
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onCreateOptionsMenu");

        final int menuRes = getMenuResource();

        if (menuRes != 0) {
            inflater.inflate(menuRes, menu);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * See {@link Fragment#onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate
     *                           any views in the fragment,
     * @param container          If non-null, this is the parent view that the fragment's UI should
     *                           be attached to.  The fragment should not add the view itself, but
     *                           this can be used to generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     *                           from a previous saved state as given here.
     * @return Return the View for the fragment's UI.
     */
    @Override
    @CallSuper
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onCreateView");

        if (savedInstanceState != null) {
            Timber.i("Lifecycle: " + getClass().getSimpleName() + " is being recreated");
        }

        final View view = inflateLayout(inflater, container);
        initializeUi(view);
        return view;
    }

    private View inflateLayout(@NonNull LayoutInflater inflater, ViewGroup container) {
        final Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater =
                bindingInflater();

        // If fragment has opted-in to viewBinding, lets gooooo
        if (bindingInflater != null) {
            Timber.d(getClass().getSimpleName() + " using viewBinding");
            _binding = bindingInflater.invoke(inflater, container, false);
            return _binding.getRoot();

        } else {
            // Else we fallback to legacy butterknife view binding
            Timber.d(getClass().getSimpleName() + " using Butterknife");
            final View view = inflater.inflate(getLayoutResource(), container, false);
            butterKnifeUnbinder = ButterKnife.bind(this, view);
            return view;
        }
    }

    /**
     * See {@link Fragment#onViewCreated(View, Bundle)}.
     *
     * @param view               The View returned by
     *                           {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     *                           from a previous saved state as given here.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onViewCreated");
        initializeUi(view);
        presenter.onViewCreated(savedInstanceState);
    }

    /**
     * Override this method to add any activity initialization logic.
     *
     * @param view the View for the fragment's UI as returned by {@link #onCreateView})
     */
    protected void initializeUi(View view) {
    }

    /**
     * Our own custom impl of deprecated Fragment#onActivityCreated. This will always be called
     * AFTER the Activity's onCreate method has successfully finished.
     */
    protected void onActivityCreated() {

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
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onResume");
        super.onResume();
        presenter.setUp(getArgumentsBundle());
        presenter.onSetUpFinished();
    }

    @Override
    @CallSuper
    public void onPause() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onPause");
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
    @CallSuper
    public void onDestroyView() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onDestroyView");
        if (butterKnifeUnbinder != null) {
            butterKnifeUnbinder.unbind();
        }
        _binding = null;
        tearDownUi();
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onStart");
        super.onStart();

        if (blockScreenshots()) {
            // NOTE:
            // We block screenshots between onStart/onDetach (rather than onAttach/onDetach). Why?

            // Normally, onAttach will be called after Activity's onCreate has completed (during
            // onStart, in fact). However, if the Activity was killed by the system and is being
            // recreated, the call will come during Activity's onCreate.

            // Since we set up our extensions in onCreate (notably after calling super.onCreate),
            // ExtensibleActivity won't have the chance to initialize before Fragments receive the
            // onAttach call, and screenshotBlockExtension will be null.

            // This is not theoretical, we've seen this in the wild.
            // See: https://stackoverflow.com/q/30297978/901465
            final String caller = this.getClass().getSimpleName();
            getParentActivity().screenshotBlockExtension.startBlockingScreenshots(caller);
        }
    }

    @Override
    public void onStop() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onStop");
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
        return ((BaseActivity) Objects.requireNonNull(getActivity())).getApplicationComponent();
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
    public void onPermissionsGranted(@NonNull String[] grantedPermissions) {
        // Do nothing
    }

    /**
     * Override this method to be notified when SOME of the requested permissions have been denied.
     */
    public void onPermissionsDenied(@NonNull String[] deniedPermissions) {
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

    /**
     * Uses requireActivity() which forces the activity to be non-null (e.g the fragment must be
     * attached to an activity). This is a rather new android addition (similar to
     * {@link Fragment#requireContext()}), which has some nice properties for callers when they
     * know that activity will be available or they want to signal that it should.
     *
     * <p>See {@link #safeGetParentActivity()}.</p>
     */
    protected BaseActivity getParentActivity() {
        return (BaseActivity) requireActivity();
    }

    /**
     * Uses getActivity() which does not force anything and can return null if the activity is
     * not available. This is useful for callers that are not sure whether the activity is available
     * (e.g sometimes it might be available and sometimes not). Yeah, that happens in android 🤷 .
     *
     * <p>See {@link #getParentActivity()}.</p>
     */
    protected Optional<BaseActivity> safeGetParentActivity() {
        return Optional.ofNullable((BaseActivity) getActivity());
    }

    protected boolean blockScreenshots() {
        return false;
    }

    @Override
    public int getMId() {
        return getId();
    }
}
