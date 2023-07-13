package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.R;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.net.NetworkInfoProvider;
import io.muun.apollo.data.os.GooglePlayServicesHelper;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.ClientVersionRepository;
import io.muun.apollo.domain.ClipboardManager;
import io.muun.apollo.domain.EmailReportManager;
import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.analytics.Analytics;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.errors.DeprecatedClientVersionError;
import io.muun.apollo.domain.errors.ExpiredSessionError;
import io.muun.apollo.domain.errors.SecureStorageError;
import io.muun.apollo.domain.errors.TooManyRequestsError;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.report.CrashReport;
import io.muun.apollo.domain.model.report.CrashReportBuilder;
import io.muun.apollo.domain.model.report.EmailReport;
import io.muun.apollo.domain.selector.LogoutOptionsSelector;
import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.apollo.presentation.app.Email;
import io.muun.apollo.presentation.app.Logcat;
import io.muun.apollo.presentation.app.Navigator;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.common.Optional;
import io.muun.common.exception.PotentialBug;
import io.muun.common.model.SessionStatus;
import io.muun.common.rx.RxHelper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import icepick.Icepick;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

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
    protected Logcat logcat;

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
    public void destroy() {

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

        analytics.report(new AnalyticsEvent.E_ERROR(AnalyticsEvent.ERROR_TYPE.GENERIC));

        // Our current error handling logic is this:
        // - If error is one of our known fatal error -> handleFatalError()
        // - Else if error is one of our known non fatal error -> handleNonFatalError()
        // - Otherwise -> handleUnknownError()
        if (!handleFatalError(error) && !handleNonFatalError(error)) {
            handleUnknownError(error);
        }
    }

    /**
     * Handle a fatal error (e.g an error that prevent users to continue using wallet correctly).
     * Returns a boolean informing whether the received error was handled (because its was fatal)
     * or not.
     */
    protected boolean handleFatalError(Throwable error) {
        final boolean isRecoverableWallet = logoutOptionsSel.isRecoverable();

        if (error instanceof DeprecatedClientVersionError && isRecoverableWallet) {
            navigator.navigateToRequestUpdate(getContext());

        } else if (error instanceof ExpiredSessionError) {

            if (isRecoverableWallet) {
                // TODO this handling sucks. The application is useless now, until Houston decides
                // to un-expire the Session. However, we can do this manually, which is much better
                // than irreversibly destroying the wallet keys.
                navigator.navigateToRequestRestart(getContext());

            } else {
                // Handle session expired "gracefully"
                view.showErrorDialog(
                        R.string.error_expired_session,
                        () -> {
                            // Do nothing
                        }
                );
            }

        } else if (error instanceof SecureStorageError && isRecoverableWallet) {

            view.showErrorDialog(
                    R.string.secure_storage_error,
                    () -> navigator.navigateToLogout(getContext())
            );

        } else {
            return false;
        }

        return true;
    }

    /**
     * Handle some common, known non fatal errors.
     * Returns a boolean informing whether the received error was handled or not.
     */
    protected boolean handleNonFatalError(Throwable error) {

        if (error instanceof UserFacingError) {
            view.showErrorDialog(
                    "" + error.getLocalizedMessage(),  // avoid null
                    () -> showErrorReportDialog(error, false)
            );
            return true;

        } else if (ExtensionsKt.isInstanceOrIsCausedByNetworkError(error)) {
            view.showErrorDialog(R.string.network_error_message);
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

    /**
     * Handle an unknown error (e.g an error for which we don't have a custom or explicit handling).
     * Our default (what this method does) is showing our send error report dialog.
     */
    protected void handleUnknownError(Throwable error) {
        // If not already logged, send it to Crashlytics:
        if (!(error instanceof PotentialBug)) {
            Timber.e(error);
        }

        showErrorReportDialog(error, true);
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

        analytics.report(new AnalyticsEvent.E_ERROR_REPORT_DIALOG());

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

    @Override
    public void sendErrorReport(Throwable error) {

        final CrashReport report = CrashReportBuilder.INSTANCE.build(error);
        analytics.attachAnalyticsMetadata(report);

        final EmailReport emailReport = emailReportManager
                .buildEmailReport(report, this.getClass().getSimpleName());

        final Intent emailIntent = Email.INSTANCE.buildEmailReportIntent(getContext(), emailReport);
        logcat.addLogsAsAttachment(emailIntent);

        if (Email.INSTANCE.hasEmailAppInstalled(getContext())) {
            getContext().startActivity(emailIntent);

        } else {
            // If user has no email client (:facepalm) let's offer to copy report to clipboard and
            // send it via any means she prefers.

            final MuunDialog muunDialog = new MuunDialog.Builder()
                    .title(R.string.error_copy_report_dialog_title)
                    .message(R.string.error_copy_report_dialog_body)
                    .positiveButton(
                            R.string.error_copy_report_dialog_yes,
                            () -> clipboardManager.copy("Muun Error Report", emailReport.getBody())
                    )
                    .negativeButton(R.string.cancel, null)
                    .build();

            view.showDialog(muunDialog);
        }
    }
}
