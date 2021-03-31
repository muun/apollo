package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.R;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.logging.CrashReport;
import io.muun.apollo.data.logging.CrashReportBuilder;
import io.muun.apollo.data.logging.EmailReport;
import io.muun.apollo.data.net.NetworkInfoProvider;
import io.muun.apollo.data.os.GooglePlayServicesHelper;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.ClientVersionRepository;
import io.muun.apollo.domain.ClipboardManager;
import io.muun.apollo.domain.EmailReportManager;
import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.errors.DeprecatedClientVersionError;
import io.muun.apollo.domain.errors.ExpiredSessionError;
import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.apollo.domain.errors.TooManyRequestsError;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.selector.LogoutOptionsSelector;
import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.apollo.presentation.analytics.Analytics;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.app.Navigator;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.common.Optional;
import io.muun.common.exception.PotentialBug;
import io.muun.common.model.SessionStatus;
import io.muun.common.rx.RxHelper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import com.scottyab.rootbeer.RootBeer;
import icepick.Icepick;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

import java.util.List;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

/**
 * Base presenter whose lifetime is the same as its view.
 */
public class BasePresenter<ViewT extends BaseView> implements Presenter<ViewT> {

    private static final String PRESENTER_STATE_KEY = "presenter_state";

    @NotNull // not true, but compatible with Kotlin lateinit var
    public ViewT view;

    /**
     * CompositeSubscription for tracking the presenter's subscriptions and unsubscribe them when
     * needed.
     *
     * <p>This can't be constructed on creation, as presenter instances can be reused during the
     * life cycle of their views. Eg: replacing a fragment and then going back to it will call two
     * times to {@code #setUp}.
     *
     * <p>This isn't initialized in {@code #setUp} though, because some non-standard life cycles
     * (eg: asking for permissions) can result {@code #subscribeTo} being called before
     * {@code #setUp}. So we render it lazily in {@code #subscribeTo}.
     */
    @Nullable
    private CompositeSubscription compositeSubscription;

    @Inject
    protected Navigator navigator;

    @Inject
    protected ExecutionTransformerFactory transformerFactory;

    // TODO: this belongs to data module, shouldn't be injected here
    @Inject
    protected NetworkInfoProvider networkInfoProvider;

    @Inject
    protected ClientVersionRepository clientVersionRepository;

    @Inject
    protected AuthRepository authRepository;

    @Inject
    protected UserSelector userSel;

    @Inject
    protected LogoutOptionsSelector logoutOptionsSel;

    @Inject
    protected Analytics analytics;

    @Inject
    protected GooglePlayServicesHelper googlePlayServicesHelper;

    @Inject
    protected ClipboardManager clipboardManager;

    @Inject
    protected EmailReportManager emailReportManager;

    @Inject
    protected BasePresenter() {
    }

    /**
     * Restores the state of the presenter.
     */
    @Override
    public final void restoreState(@Nullable Bundle state) {
        if (state != null) {
            Icepick.restoreInstanceState(this, state.getBundle(PRESENTER_STATE_KEY));
        }
    }

    /**
     * Saves the state of the presenter.
     */
    @Override
    public final void saveState(@NotNull Bundle state) {

        final Bundle bundle = new Bundle();
        Icepick.saveInstanceState(this, bundle);

        state.putBundle(PRESENTER_STATE_KEY, bundle);
    }

    /**
     * Override this method to add any initialization logic that needs to happen at view creation,
     * e.g at Activity/Fragment#onCreate.
     */
    @Override
    public void onViewCreated(Bundle savedInstanceState) {

    }

    /**
     * Override this method to add any initialization logic that the presenter needs.
     */
    @Override
    @CallSuper
    public void setUp(@NotNull Bundle arguments) {
        setUpNetworkInfo();
        setUpDeprecatedClientVersionCheck();
        setUpSessionExpiredCheck();
    }

    @Override
    @CallSuper
    public void afterSetUp() {
        // Report we entered this screen, if needed:
        Optional.ofNullable(getEntryEvent()).ifPresent(analytics::report);
    }

    /**
     * Override this method to add any clean up logic that the presenter needs.
     */
    @Override
    @CallSuper
    public void tearDown() {
        tearDownNetworkInfo();

        if (compositeSubscription != null) {
            compositeSubscription.unsubscribe();
            compositeSubscription = null;
        }
    }

    @Override
    public void setView(@NotNull ViewT view) {
        this.view = view;
    }

    @NotNull
    protected Context getContext() {
        return view.getViewContext();
    }

    /**
     * Return an event to be logged when setting up this presenter, or null.
     */
    @Nullable
    protected AnalyticsEvent getEntryEvent() {
        return null;
    }

    protected void assertGooglePlayServicesPresent() {
        final int available = googlePlayServicesHelper.isAvailable();
        if (available != GooglePlayServicesHelper.AVAILABLE) {
            view.showPlayServicesDialog(googlePlayServicesHelper.showDownloadDialog(available));
        }
    }

    /**
     * Check if there's an email app installed on the device (without actually resolving intent,
     * which can cause an Intent chooser from the OS to pop up).
     */
    public boolean hasEmailAppInstalled() {
        final Intent intent = getEmailIntent();
        final PackageManager pm = getContext().getPackageManager();

        final List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);

        // This is to account for a weird case that happens on Android emulators
        // See: https://stackoverflow.com/a/31051791/901465
        final boolean theOnlyAppInstalledIsAndroidFallback = list.size() == 1
                && list.get(0).activityInfo.packageName.equals("com.android.fallback");

        return list.size() != 0 && !theOnlyAppInstalledIsAndroidFallback;
    }

    /**
     * Subscribes this Presenter to an Observable, ensuring that errors are properly handled and
     * the view life-cycle considered.
     */
    protected <T> Subscription subscribeTo(Observable<T> observable) {
        return subscribeTo(observable, RxHelper::nop);
    }

    /**
     * Subscribes this Presenter to an Observable, ensuring that errors are properly handled and
     * the view life-cycle considered.
     */
    protected <T> Subscription subscribeTo(Observable<T> observable, @NotNull Action1<T> onNext) {
        if (compositeSubscription == null) {
            compositeSubscription = new CompositeSubscription();
        }

        final Subscription subscription = observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onNext, this::handleError);

        compositeSubscription.add(subscription);

        return subscription;
    }

    /**
     * Return a composable Transformer that begins the execution of an Observable in background,
     * and continues after this point in the UI thread.
     */
    protected <T> Observable.Transformer<T, T> getAsyncExecutor() {
        return transformerFactory.getAsyncExecutor();
    }

    /**
     * Handle an Error appropriately, deciding what to do and invoking View methods as needed.
     */
    @Override
    public void handleError(Throwable error) {
        // All errors thrown by Observables bound to Presenters will end up here, as well as those
        // that were manually reported by invoking this method.

        // Some Exceptions will be handled with specialized code, but in most cases we'll simply
        // show a message to the user. For subclasses of `UserFacingError`, this will be the
        // Exception message; for other classes, a generic one.

        // We always log potential bugs:
        if (error instanceof PotentialBug) {
            Timber.e(error);
        }

        final boolean wasHandled = maybeHandleFatalError(error)
                || maybeHandleNonFatalError(error)
                || maybeHandleUnknownError(error);

        // Unlike internal methods, we don't return a boolean here. This is mostly due to legacy
        // reasons, but so far there has been no need.
    }

    protected boolean maybeHandleFatalError(Throwable error) {
        if (error instanceof DeprecatedClientVersionError && canLogout()) {
            navigator.navigateToRequestUpdate(getContext());

        } else if (error instanceof ExpiredSessionError) {

            if (canLogout()) {
                // TODO this handling sucks. The application is useless now, until Houston decides
                // to un-expire the Session. However, we can do this manually, which is much better
                // than irreversibly destroying the wallet keys.
                navigator.navigateToRequestRestart(getContext());

            } else {
                // Handle session expired "gracefully"
                view.showErrorDialog(
                        getContext().getString(R.string.error_expired_session),
                        () -> {
                            // Do nothing
                        }
                );
            }

        } else if (error instanceof SecureStorageError && canLogout()) {

            view.showErrorDialog(getContext().getString(R.string.secure_storage_error),
                    () -> navigator.navigateToLogout(getContext())
            );

        } else {
            return false;
        }

        return true;
    }

    /**
     * Protected so some special presenters e.g SignupPresenter) can answer this directly without
     * needing anything from the local data (which may or may not be available).
     */
    protected boolean canLogout() {
        return logoutOptionsSel.canDeleteWallet();
    }

    /**
     * Try to handle some common, known non fatal errors. Return whether the error's handled or not.
     */
    protected boolean maybeHandleNonFatalError(Throwable error) {

        if (error instanceof UserFacingError) {
            view.showErrorDialog(
                    error.getLocalizedMessage(),
                    () -> showErrorReportDialog(error, false)
            );
            return true;

        } else if (ExtensionsKt.isInstanceOrIsCausedByNetworkError(error)) {
            view.showErrorDialog(
                    getContext().getString(R.string.network_error_message),
                    () -> showErrorReportDialog(error, false)
            );
            return true;

        } else if (error instanceof TooManyRequestsError) {
            view.showErrorDialog(
                    getContext().getString(R.string.error_generic),
                    () -> { // Do nothing
                    }
            );

            return true;
        }

        return false;
    }

    protected boolean maybeHandleUnknownError(Throwable error) {
        // If not already logged, send it to Crashlytics:
        if (!(error instanceof PotentialBug)) {
            Timber.e(error);
        }

        showErrorReportDialog(error, true);

        return true;
    }

    /**
     * Returns a Long argument by its key from an argument, and removes it.
     */
    protected Optional<Long> takeLongArgument(@Nullable Bundle bundle, @NotNull String key) {
        if (bundle == null || !bundle.containsKey(key)) {
            return Optional.empty();
        }

        final long value = bundle.getLong(key);
        bundle.remove(key);

        return Optional.of(value);
    }

    /**
     * Returns a String argument by its key from an argument, and removes it.
     */
    protected Optional<String> takeStringArgument(@NotNull Bundle bundle, @NotNull String key) {
        if (!bundle.containsKey(key)) {
            return Optional.empty();
        }

        final String value = bundle.getString(key);
        bundle.remove(key);

        return Optional.ofNullable(value);
    }

    /**
     * Ensures a condition involving the arguments of a presenter is true. If it fails, it logs the
     * error, and closes the activity.
     *
     * @param condition The condition to be tested.
     * @param fieldName The name of the argument.
     */
    protected void checkArgument(boolean condition, String fieldName) {

        if (!condition) {

            final IllegalArgumentException error = new IllegalArgumentException(
                    getClass().getName() + "#" + fieldName + " is invalid"
            );

            Timber.e(error);

            view.showErrorDialog(
                    getContext().getString(R.string.error_generic),
                    () -> showErrorReportDialog(error, false),
                    () -> view.finishActivity()
            );
        }
    }

    /**
     * Returns `true` if a permission is granted.
     */
    protected boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(getContext(), permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    protected void onNetworkConnectionChange(boolean isConnected) {
    }

    @VisibleForTesting
    protected void setUpNetworkInfo() {
        networkInfoProvider.startReceiving();

        final Observable<?> observable = networkInfoProvider
                .watchNetworkInfo()
                .compose(getAsyncExecutor())
                .doOnNext(info -> {
                    final boolean isConnected = info
                            .map(NetworkInfo::isConnected)
                            .orElse(false);

                    onNetworkConnectionChange(isConnected);
                });

        subscribeTo(observable);
    }

    @VisibleForTesting
    protected void tearDownNetworkInfo() {
        networkInfoProvider.stopReceiving();
    }

    @VisibleForTesting
    protected void setUpDeprecatedClientVersionCheck() {
        final Observable<?> observable = clientVersionRepository.watchMinClientVersion()
                .doOnNext(it -> it.ifPresent(this::checkClientVersion));

        subscribeTo(observable);
    }

    @VisibleForTesting
    protected void setUpSessionExpiredCheck() {
        final Observable<?> observable = authRepository.watchSessionStatus()
                .doOnNext(it -> it.ifPresent(this::checkSessionStatus));

        subscribeTo(observable);
    }

    private void checkClientVersion(int minClientVersion) {
        if (shouldCheckClientState() && Globals.INSTANCE.getVersionCode() < minClientVersion) {
            throw new DeprecatedClientVersionError();
        }
    }

    private void checkSessionStatus(SessionStatus status) {
        if (shouldCheckClientState() && status == SessionStatus.EXPIRED) {
            throw new ExpiredSessionError();
        }
    }

    protected boolean shouldCheckClientState() {
        return true; // allow subclasses to override
    }

    protected <T> Observable.Transformer<ActionState<T>, T> handleStates(
            @Nullable Action1<Boolean> handleLoading,
            @Nullable Action1<Throwable> handleError) {

        return observable -> observable
                .doOnNext(state -> {
                    if (handleLoading != null) {
                        handleLoading.call(state.isLoading());
                    }

                    if (handleError != null && state.isError()) {
                        handleError.call(state.getError());
                    }
                })
                .filter(ActionState::isValue)
                .map(ActionState::getValue);
    }

    /**
     * Show our famous send error report dialog. Receives a "standalone" flag to signal if this
     * dialog stands on its own or is a followup of an error dialog.
     */
    private void showErrorReportDialog(Throwable error, boolean standalone) {

        final MuunDialog.Builder builder = new MuunDialog.Builder()
                .layout(R.layout.dialog_custom_layout)
                .positiveButton(
                        R.string.error_send_report_dialog_yes,
                        () -> sendErrorReport(error)
                )
                .negativeButton(R.string.cancel, null);

        if (standalone) {
            builder.title(R.string.error_send_report_dialog_title);
            builder.message(R.string.error_send_report_dialog_body);

        } else {
            builder.message(R.string.error_send_report_dialog_body_short);
        }

        final MuunDialog muunDialog = builder.build();

        view.showDialog(muunDialog);
    }

    private void sendErrorReport(Throwable error) {

        final CrashReport report = CrashReportBuilder.INSTANCE.build(error);
        analytics.attachAnalyticsMetadata(report);

        final String presenterName = this.getClass().getSimpleName();
        final boolean isRootedDevice = new RootBeer(getContext()).isRooted();

        final EmailReport emailReport = emailReportManager
                .buildEmailReport(report, presenterName, isRootedDevice);

        final String subjectPrefix = getContext().getString(R.string.error_report_email_subject);
        final String subject = emailReport.subject(subjectPrefix);
        final String body = emailReport.getBody();
        final Intent emailIntent = composeEmail("support@muun.com", subject, body);

        if (hasEmailAppInstalled()) {
            getContext().startActivity(emailIntent);

        } else {
            // If user has no email client (:facepalm) let's offer to copy report to clipboard and
            // send it via any means she prefers.

            final MuunDialog muunDialog = new MuunDialog.Builder()
                    .title(R.string.error_copy_report_dialog_title)
                    .message(R.string.error_copy_report_dialog_body)
                    .positiveButton(
                            R.string.error_copy_report_dialog_yes,
                            () -> clipboardManager.copy("Muun Error Report", body)
                    )
                    .negativeButton(R.string.cancel, null)
                    .build();

            view.showDialog(muunDialog);
        }
    }



    private Intent composeEmail(String address, String subject, String body) {
        return composeEmail(new String[] { address }, subject, body);
    }

    /**
     * Correct way of opening email intent. According to android docs:
     * https://developer.android.com/guide/components/intents-common.html#Email
     */
    private Intent composeEmail(String[] addresses, String subject, String body) {
        final Intent intent = getEmailIntent();
        intent.putExtra(Intent.EXTRA_EMAIL, addresses);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        return intent;
    }

    private Intent getEmailIntent() {
        final Intent intent = new Intent(Intent.ACTION_SENDTO);
        return intent.setData(Uri.parse("mailto:")); // only email apps should handle this
    }
}
