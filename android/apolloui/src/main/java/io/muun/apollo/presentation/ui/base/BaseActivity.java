package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.R;
import io.muun.apollo.data.logging.Crashlytics;
import io.muun.apollo.data.logging.LoggingContext;
import io.muun.apollo.domain.action.UserActions;
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
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.FloatingOverflowMenu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.MenuRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import butterknife.ButterKnife;
import icepick.Icepick;
import rx.functions.Action0;
import rx.functions.Action1;
import timber.log.Timber;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;


public abstract class BaseActivity<PresenterT extends Presenter> extends ExtensibleActivity
        implements BaseView, PermissionRequester, ExternalResultExtension.DelegableCaller {

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
    UserActions userActions;

    @Inject
    protected LinkBuilder linkBuilder;

    private ActivityComponent component;

    protected abstract void inject();

    /**
     * Returns the id of the resource with the layout of the activity.
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
    protected void setUpExtensions() {
        inject();
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
            super.onCreate(savedInstanceState);

            Icepick.restoreInstanceState(this, savedInstanceState);

            setUpLayout();
            initializePresenter(savedInstanceState);
            initializeUi();

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
        tearDownUi();
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
            presenter = (PresenterT) getExtension(PersistentPresenterExtension.class)
                    .get(savedInstanceState, presenter);
        }

        setPresenterView();
        presenter.onViewCreated(savedInstanceState);
        presenter.restoreState(savedInstanceState);
    }

    @SuppressWarnings("unchecked")
    protected void setPresenterView() {
        presenter.setView(this);
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
        presenter.saveState(outState);
    }

    @Override
    @CallSuper
    protected void onResume() {
        try {
            super.onResume();
            LoggingContext.setLocale(ExtensionsKt.locale(this).toString());
            if (blockScreenshots()) {
                screenshotBlockExtension.startBlockingScreenshots();
            }

            userActions.updateContactsPermissionState(
                    allPermissionsGranted(android.Manifest.permission.READ_CONTACTS)
            );
            presenter.setUp(getArgumentsBundle());
            presenter.afterSetUp();
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
        super.onPause();
        presenter.tearDown();
        if (blockScreenshots()) {
            screenshotBlockExtension.stopBlockingScreenshots();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // Avoid leaving soft keyboard shown. When coming back to lock screen it may be left hanging
        // around. Every screen should handle showing it again on their onResume method.
        UiUtils.lastResortHideKeyboard(this);
    }

    private void setUpLayout() {
        setAnimation();
        setContentView(getLayoutResource());
        ButterKnife.bind(this);
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
                Crashlytics.logBreadcrumb("shouldIgnoreBackAndExit() failed: " + e.getMessage());
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
        super.finish();
    }

    @Override
    public void finishActivity() {
        supportFinishAfterTransition();
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
