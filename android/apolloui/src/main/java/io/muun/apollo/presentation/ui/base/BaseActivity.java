package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.R;
import io.muun.apollo.data.logging.LoggingContext;
import io.muun.apollo.domain.action.permission.UpdateContactsPermissionStateAction;
import io.muun.apollo.domain.action.permission.UpdateNotificationPermissionStateAction;
import io.muun.apollo.domain.errors.BugDetected;
import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.apollo.presentation.app.ApolloApplication;
import io.muun.apollo.presentation.app.di.ApplicationComponent;
import io.muun.apollo.presentation.ui.activity.extension.AlertDialogExtension;
import io.muun.apollo.presentation.ui.activity.extension.ApplicationLockExtension;
import io.muun.apollo.presentation.ui.activity.extension.ExternalResultExtension;
import io.muun.apollo.presentation.ui.activity.extension.ExternalResultExtension.Caller;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.activity.extension.OverflowMenuExtension;
import io.muun.apollo.presentation.ui.activity.extension.PermissionManagerExtension;
import io.muun.apollo.presentation.ui.activity.extension.PermissionManagerExtension.PermissionRequester;
import io.muun.apollo.presentation.ui.activity.extension.PersistentPresenterExtension;
import io.muun.apollo.presentation.ui.activity.extension.ScreenshotBlockExtension;
import io.muun.apollo.presentation.ui.activity.extension.ShakeToDebugExtension;
import io.muun.apollo.presentation.ui.activity.extension.SnackBarExtension;
import io.muun.apollo.presentation.ui.base.di.ActivityComponent;
import io.muun.apollo.presentation.ui.utils.LinkBuilder;
import io.muun.apollo.presentation.ui.utils.OS;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.FloatingOverflowMenu;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.MenuRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.viewbinding.ViewBinding;
import butterknife.ButterKnife;
import icepick.Icepick;
import kotlin.jvm.functions.Function1;
import rx.functions.Action0;
import rx.functions.Action1;
import timber.log.Timber;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;


public abstract class BaseActivity<PresenterT extends Presenter> extends ExtensibleActivity
        implements BaseView, PermissionRequester, ExternalResultExtension.DelegableCaller {

    /**
     * Reserved property for View Binding use, will be automatically cleaned up by this parent class
     * in {@link #onDestroy()} ()}.
     * TODO: this should be a protected var + private set when we kotlinize this mofo
     */
    private ViewBinding _binding;

    protected ViewBinding getBinding() {
        return _binding;
    }

    @Inject
    @NotNull // not true, but compatible with Kotlin lateinit var
    protected PresenterT presenter;

    @Inject
    protected ApplicationLockExtension applicationLockExtension;

    @Inject
    ExternalResultExtension externalResultExtension;

    @Inject
    PermissionManagerExtension permissionManagerExtension;

    @Inject
    ShakeToDebugExtension shakeToDebugExtension;

    @Inject
    AlertDialogExtension alertDialogExtension;

    @Inject
    SnackBarExtension snackBarExtension;

    @Inject
    OverflowMenuExtension overflowMenuExtension;

    @Inject
    PersistentPresenterExtension persistentPresenterExtension;

    @Inject
    ScreenshotBlockExtension screenshotBlockExtension;

    @Inject
    UpdateContactsPermissionStateAction updateContactsPermissionState;

    @Inject
    UpdateNotificationPermissionStateAction updateNotificationPermissionState;

    @Inject
    protected LinkBuilder linkBuilder;

    private ActivityComponent component;

    protected abstract void inject();

    /**
     * Returns the id of the resource with the layout of the activity.
     * TODO rm this once all activities have successfully migrated from butterKnife to view binding.
     */
    @LayoutRes
    protected abstract int getLayoutResource();

    /**
     * Override this method to opt in to use View Binding. This will turn into an abstract method
     * to force all subclasses to implement once we finish the transition from ButterKnife to
     * ViewBinding. For now, we leave this default impl to not bother classes that haven't
     * transitioned yet.
     */
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
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
    protected void setUpExtensions() {
        addExtension(applicationLockExtension);
        addExtension(externalResultExtension);
        addExtension(shakeToDebugExtension);
        addExtension(permissionManagerExtension);
        addExtension(alertDialogExtension);
        addExtension(snackBarExtension);
        addExtension(overflowMenuExtension);
        addExtension(persistentPresenterExtension);
        addExtension(screenshotBlockExtension);
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            inject(); // Inject asap, presenters are required early in activity/fragment lifecycle
            super.onCreate(savedInstanceState);

            Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onCreate");

            if (savedInstanceState != null) {
                Timber.i("Lifecycle: " + getClass().getSimpleName() + " is being recreated");
            }

            Icepick.restoreInstanceState(this, savedInstanceState);

            setUpLayout();
            initializePresenter(savedInstanceState);
            initializeUi();

            presenter.onViewCreated(savedInstanceState);
        } catch (SecureStorageError e) {
            // Avoid crashing on weird Android Keystore errors. Redundantly report error with
            // extra metadata and offer some explanation to the user with the option to send
            // error report email for maximum exposure and data points.

            Timber.e(e);
            showErrorDialog(
                    R.string.secure_storage_error_avoid_crash_non_logout,
                    () -> presenter.sendErrorReport(e)
            );
        }
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onDestroy");
        tearDownUi();
        _binding = null;
        super.onDestroy();
    }

    /**
     * Override this method to add any activity initialization logic.
     */
    protected void initializeUi() {
    }

    /**
     * Override this method to add any clean up logic.
     */
    protected void tearDownUi() {
        presenter.destroy();
    }

    protected void initializePresenter(@Nullable Bundle savedInstanceState) {
        if (isPresenterPersistent()) {
            // NOTE: suppressing unchecked cast due to limited generic type inference in our base
            // classes (circular generic reference between BaseActivity->BasePresenter->BaseView).
            // This limitation has spilled over several classes (e.g PersistentPresenterExtension).
            // TODO: solve circular generic reference in base classes, get rid of unchecked cast
            //noinspection unchecked
            presenter = (PresenterT) getExtension(PersistentPresenterExtension.class)
                    .get(savedInstanceState, presenter);
        }

        setPresenterView();
        presenter.restoreState(savedInstanceState);
    }

    @SuppressWarnings("unchecked")
    protected void setPresenterView() {
        presenter.setView(this);
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(Bundle outState) {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onSaveInstanceState");
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
        presenter.saveState(outState);
    }

    @Override
    @CallSuper
    protected void onResume() {
        try {
            Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onResume");
            super.onResume();
            LoggingContext.setLocale(ExtensionsKt.locale(this).toString());
            if (blockScreenshots()) {
                screenshotBlockExtension.startBlockingScreenshots(this.getClass().getSimpleName());
            }

            // Keep PermissionState of important permissions up to date. This is important to handle
            // PERMANENTLY_DENIED (aka Never Ask Again). See each action's javadoc for more details.
            updateContactsPermissionState.run(
                    allPermissionsGranted(android.Manifest.permission.READ_CONTACTS)
            );

            if (OS.supportsNotificationRuntimePermission()) {
                updateNotificationPermissionState.run(hasNotificationsPermission());
            }

            presenter.setUp(getArgumentsBundle());
            presenter.onSetUpFinished();
        } catch (SecureStorageError e) {
            // Avoid crashing on weird Android Keystore errors. Redundantly report error with
            // extra metadata and offer some explanation to the user with the option to send
            // error report email for maximum exposure and data points.

            Timber.e(e);
            showErrorDialog(
                    R.string.secure_storage_error_avoid_crash_non_logout,
                    () -> presenter.sendErrorReport(e)
            );
        }
    }

    @Override
    @CallSuper
    protected void onPause() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onPause");
        super.onPause();
        presenter.tearDown();
        if (blockScreenshots()) {
            screenshotBlockExtension.stopBlockingScreenshots(this.getClass().getSimpleName());
        }
    }

    @Override
    public void onStop() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onStop");
        super.onStop();
        // Avoid leaving soft keyboard shown. When coming back to lock screen it may be left hanging
        // around. Every screen should handle showing it again on their onResume method.
        UiUtils.lastResortHideKeyboard(this);
    }

    private void setUpLayout() {
        setAnimation();

        final Function1<LayoutInflater, ViewBinding> bindingInflater = bindingInflater();

        // If activity has opted-in to viewBinding, lets gooooo
        if (bindingInflater != null) {
            Timber.d(getClass().getSimpleName() + " using viewBinding");
            _binding = bindingInflater.invoke(LayoutInflater.from(this));
            setContentView(_binding.getRoot());

        } else {
            // Else we fallback to legacy butterknife view binding
            Timber.d(getClass().getSimpleName() + " using Butterknife");
            setContentView(getLayoutResource());
            ButterKnife.bind(this);
        }
    }

    /**
     * Left empty by default, but any activity can choose to override it.
     */
    protected void setAnimation() {

    }

    @NotNull
    @Override
    public Context getViewContext() {
        return this;
    }

    @NotNull
    @Override
    public Bundle getArgumentsBundle() {
        final Bundle extras = getIntent().getExtras();

        if (extras == null) {
            return new Bundle();
        }

        return extras;
    }

    @NotNull
    protected ActivityComponent getComponent() {

        if (component == null) {
            component = getApplicationComponent().activityComponent();
        }

        return component;
    }

    @NotNull
    public ApplicationComponent getApplicationComponent() {

        return ((ApolloApplication) getApplication()).getApplicationComponent();
    }

    /**
     * Safely try and show a simple test Toast.
     * You should not call this method from any thread other than Android's MainThread. It's a
     * coding error. We're just adding a safety measure to avoid fatal crashes in weird/edge
     * situations.
     */
    @Override
    public void showTextToast(String text) {
        // Shouldn't happen but we're adding this to prevent weird crashes.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();

        } else {
            Timber.e(new BugDetected("Attempted to show toast from background thread"));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;

        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#onBackPressed");
        if (!shouldIgnoreBackAndExit()) {
            super.onBackPressed();
        }
    }

    protected boolean shouldIgnoreBackAndExit() {
        final boolean shouldIgnoreBackAndExit = applicationLockExtension.isShowingLockOverlay();

        if (shouldIgnoreBackAndExit) {

            try {
                // Ugly hack to "simulate home button press". We need to exit "smoothly", sending
                // it to the background, as when Home Button is pressed. This gets the job done.
                // See: https://stackoverflow.com/questions/21253303/exit-android-app-on-back-pressed
                final Intent intent = new Intent()
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                startActivity(intent);
            } catch (Exception e) {

                // Tackling weird `Can't change activity type once set:` crash. See:
                // https://stackoverflow.com/questions/55005798/illegalstateexception-cant-change-activity-type-once-set
                Timber.i("shouldIgnoreBackAndExit() failed: " + e.getMessage());
                Timber.e(e);

                // On this weird case we just ignore back (user can try pin again or choose to kill
                // the app). Should we finishAffinity() + finishActivity() instead?
            }
        }

        return shouldIgnoreBackAndExit;
    }

    @Deprecated // Use BaseActivity#finishActivity() instead
    @Override
    public void finish() {
        Timber.d("Lifecycle: " + getClass().getSimpleName() + "#finish");
        super.finish();
    }

    @Override
    public void finishActivity() {
        Timber.i("Finishing Activity: " + getClass().getSimpleName());
        supportFinishAfterTransition();
    }

    /**
     * Determine whether you have been granted the PUSH NOTIFICATION permission.
     * NOTE: we single this permission out since it was only recently introduced (api level 33) and
     * thus requires to only be referenced in new api leves (see @RequiresApi annotation).
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public final boolean hasNotificationsPermission() {
        return allPermissionsGranted(Manifest.permission.POST_NOTIFICATIONS);
    }

    /**
     * Determine whether you have been granted some permissions.
     */
    public final boolean allPermissionsGranted(String... permissions) {
        return permissionManagerExtension.allPermissionsGranted(permissions);
    }

    /**
     * Request some permissions to be granted to this application.
     */
    public final void requestPermissions(String... permissions) {
        permissionManagerExtension.requestPermissions(this, permissions);
    }

    /**
     * Request some permissions to be granted to this application.
     */
    public final void requestPermissions(PermissionRequester requester, String... permissions) {
        permissionManagerExtension.requestPermissions(requester, permissions);
    }

    /**
     * Gets whether you can show UI with rationale for requesting a permission.
     * Return false if the permission was denied with the 'Never ask again' checkbox checked.
     * See: {@link PermissionManagerExtension#canShowRequestPermissionRationale(String)}
     */
    public final boolean canShowRequestPermissionRationale(String permission) {
        return permissionManagerExtension.canShowRequestPermissionRationale(permission);
    }

    /**
     * Needed to implement PermissionRequester interface. Should never be called.
     */
    @Override
    public int getMId() {
        return System.identityHashCode(this);
    }

    /**
     * Override this method to be notified when ALL requested permissions have been granted.
     */
    public void onPermissionsGranted(String[] grantedPermissions) {
    }

    /**
     * Override this method to be notified when SOME of the requested permissions have been denied.
     */
    public void onPermissionsDenied(String[] deniedPermissions) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final int menuRes = getMenuResource();

        if (menuRes != 0) {
            getMenuInflater().inflate(menuRes, menu);
        }

        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Convenience method to show one of our @code{DrawerDialogFragment}.
     */
    public void showDrawerDialog(DialogFragment dialog) {
        dialog.show(getSupportFragmentManager(), null);
    }

    /**
     * Request an External Result. A caller to handle it is required. This is a clever way to
     * handle "external job" (e.g startActivityForResult) scenarios, while also being resilient
     * to activity and/or app destruction while on background.
     */
    public void requestExternalResult(Caller view, int requestCode, Intent intent) {
        getExtension(ExternalResultExtension.class)
                .startActivityForResult(view, requestCode, intent);
    }

    /**
     * Request a result from a DialogFragment. Although it's not super clear why this needs to
     * make use of out ExternalResultExtension#Caller mechanism, we handle a DialogFragment result
     * as if it were an Activity result (its convenient). This pattern is recommended by the Android
     * docs. Cool, I guess.
     */
    public String requestExternalResult(Caller caller, int requestCode, DialogFragment dialog) {
        return getExtension(ExternalResultExtension.class)
                .showDialogForResult(caller, requestCode, dialog);
    }

    /**
     * Request an External Result but without specifying a Caller to handle it. This allows us
     * to set the current activity as the Caller and allows some activities to be designated as
     * "Delegates" of the Extenernal Result (to be in charge of handling and/or possibly
     * dispatching it to the appropriate call site).
     *
     * <p>A slightly hackish way around for our ExternalResultExtension#Caller mechanism to work
     * with viewPager fragments. This is needed because apparently, fragments id are not necessarily
     * unique and, in the case of a viewPager, all fragments have the same id
     * (the id of the container of the fragments).
     * </p>
     */
    public void requestDelegatedExternalResult(int requestCode, Intent intent) {
        requestExternalResult(this, requestCode, intent);
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public Caller getDelegateCaller() {
        return null;
    }

    /**
     * Handle a DialogFragment result as if it were an Activity result. This pattern is
     * recommended by the Android docs. Cool, I guess.
     */
    public void onDialogResult(DialogFragment dialog, int resultCode, Intent result) {
        onActivityResult(dialog.getTargetRequestCode(), resultCode, result);
    }

    @Override
    public void showPlayServicesDialog(Action1<Activity> showDialog) {
        showDialog.call(this);
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
        alertDialogExtension.showErrorDialog(resId, followupAction, null);
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
        showErrorDialog(errorMsg, followupAction, null);
    }

    /**
     * Show a simple, standard muun error dialog.
     */
    @Override
    public void showErrorDialog(CharSequence errorMsg, Action0 followup, Action0 onDismiss) {
        alertDialogExtension.showErrorDialog(errorMsg, followup, onDismiss);
    }

    /**
     * Show an AlertDialog.
     */
    @Override
    public void showDialog(MuunDialog dialog) {
        alertDialogExtension.showDialog(dialog);
    }

    @Override
    public void dismissDialog() {
        alertDialogExtension.dismissDialog();
    }

    /**
     * Show an indefinite snack bar.
     */
    public void showSnackBar(int messageResId) {
        snackBarExtension.showSnackBarIndefinite(messageResId);
    }

    /**
     * Show an indefinite snack bar, of custom height.
     */
    public void showSnackBar(int messageResId, boolean dismissible, Float height) {
        snackBarExtension.showSnackBarIndefinite(messageResId, dismissible, height);
    }

    /**
     * Dismiss the snack bar being displayed (or do nothing if none is being displayed).
     */
    public void dismissSnackBar() {
        snackBarExtension.dismissSnackBar();
    }

    @NotNull // not true, but compatible with Kotlin lateinit var
    public PresenterT getPresenter() {
        return presenter; // make available to fragment presenters
    }

    protected void setUpOverflowMenu(FloatingOverflowMenu.Builder builder) {
        getExtension(OverflowMenuExtension.class).setUpOverFlowMenu(builder);
    }

    protected boolean isPresenterPersistent() {
        return false;
    }

    protected boolean blockScreenshots() {
        return false;
    }
}
