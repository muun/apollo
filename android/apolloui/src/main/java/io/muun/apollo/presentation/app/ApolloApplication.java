package io.muun.apollo.presentation.app;

import io.muun.apollo.data.async.tasks.MuunWorkerFactory;
import io.muun.apollo.data.debug.HeapDumper;
import io.muun.apollo.data.di.DaggerDataComponent;
import io.muun.apollo.data.di.DataComponent;
import io.muun.apollo.data.di.DataModule;
import io.muun.apollo.data.external.DataComponentProvider;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.external.UserFacingErrorMessages;
import io.muun.apollo.data.fs.LibwalletDataDirectory;
import io.muun.apollo.data.logging.Crashlytics;
import io.muun.apollo.data.logging.LoggingContext;
import io.muun.apollo.data.logging.MuunTree;
import io.muun.apollo.data.preferences.FeaturesRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.data.preferences.migration.PreferencesMigrationManager;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.BackgroundTimesService;
import io.muun.apollo.domain.NightModeManager;
import io.muun.apollo.domain.action.session.DetectAppUpdateAction;
import io.muun.apollo.domain.analytics.Analytics;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.NightMode;
import io.muun.apollo.domain.selector.BitcoinUnitSelector;
import io.muun.apollo.presentation.app.di.ApplicationComponent;
import io.muun.apollo.presentation.app.di.DaggerApplicationComponent;
import io.muun.apollo.presentation.ui.utils.UserFacingErrorMessagesImpl;
import io.muun.common.exception.MissingCaseError;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.emoji2.text.EmojiCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDex;
import androidx.work.Configuration;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.jakewharton.threetenabp.AndroidThreeTen;
import rx.plugins.RxJavaHooks;
import timber.log.Timber;

import java.util.Iterator;
import java.util.ServiceLoader;
import javax.inject.Inject;
import javax.money.spi.CurrencyProviderSpi;
import javax.validation.constraints.NotNull;

/**
 * Application code shared among all flavors.
 */
public abstract class ApolloApplication extends Application
        implements DataComponentProvider, Configuration.Provider, DefaultLifecycleObserver {

    private ApplicationComponent applicationComponent;

    private DataComponent dataComponent;

    @Inject
    PreferencesMigrationManager preferencesMigrationManager;

    @Inject
    Analytics analytics;

    @Inject
    ApplicationLockManager lockManager;

    @Inject
    LibwalletDataDirectory libwalletDataDirectory;

    @Inject
    NightModeManager nightModeManager;

    @Inject
    DetectAppUpdateAction detectAppUpdate;

    @Inject
    BackgroundTimesService backgroundTimesService;

    @Inject
    FeaturesRepository featuresRepository;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        initializeStaticSingletons();

        HeapDumper.init(this);
        Crashlytics.init(this);
        StrictMode.INSTANCE.init();

        ensureCurrencyServicesLoaded();

        AndroidThreeTen.init(this);

        // Ignore tracking events for Firebase Test Lab devices
        if (isFirebaseTestLabDevice()) {
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false);
        }

        initializeDagger();

        detectAppUpdate.run();

        setNightMode();

        setupDebugTools();

        initializeLibwallet();

        migrateSharedPreferences();

        gcmKeepAlive();

        loadBigQueryPseudoId();

        // Register lifecycle observer for app background/foreground/terminate tracking
        // Apparently this won't correctly handle/track app crashes or ANRs.
        // See: https://stackoverflow.com/a/44461605/901465 (and its comments)
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        registerReceiver(lockWhenDisplayIsOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        RxJavaHooks.enableAssemblyTracking();

        EmojiCompat.init(new BundledEmojiCompatConfig(this));

        if (lockManager.isLockConfigured()) {
            lockManager.setLock();
        }
    }

    private void loadBigQueryPseudoId() {
        analytics.loadBigQueryPseudoId();
    }

    private void setNightMode() {
        final NightMode userPreference = nightModeManager.get();

        switch (userPreference) {
            case DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;

            case LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;

            case FOLLOW_SYSTEM:
                break; // Shouldn't need to do anything, since current theme is provided by system

            default:
                throw new MissingCaseError(userPreference, "NighMode preference");
        }
    }

    /**
     * <pre>
     * See <a href="https://developer.android.com/topic/libraries/architecture/workmanager/
     * advanced/custom-configuration#implement-configuration-provider">WorkManager docs</a>.
     * </pre>
     * Shortened URL: <a href="https://shorturl.at/aflsA">...</a>
     */
    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        Timber.d("[MuunWorkerFactory] Application#getWorkManagerConfiguration()");
        final int loggingLevel = Globals.INSTANCE.isReleaseBuild() ? Log.ERROR : Log.VERBOSE;
        return new Configuration.Builder()
                .setWorkerFactory(new MuunWorkerFactory(this))
                .setMinimumLoggingLevel(loggingLevel)
                .build();
    }

    /**
     * Detect Google Play Pre-Launch report automatic tests, by identifying Firebase Test Lab
     * devices, to ignore tracking events from them.
     * See:
     * <a href="https://firebase.google.com/docs/test-lab/android/android-studio">...</a>
     * <a href="https://stackoverflow.com/a/45070039/901465">...</a>
     */
    private boolean isFirebaseTestLabDevice() {
        return "true".equals(Settings.System.getString(getContentResolver(), "firebase.test.lab"));
    }

    /**
     * This configures logging and debugging utilities. It has an override in ApolloDebugApplication
     * for additional tooling.
     */
    @CallSuper
    protected void setupDebugTools() {
        Timber.plant(new MuunTree());

        final boolean isDebug = Globals.INSTANCE.isDebugBuild();

        // Use logcat only for debug builds:
        LoggingContext.setSendToLogcat(isDebug);

        // Use Crashlytics always:
        LoggingContext.setSendToCrashlytics(true);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        // NOTE: this triggers when the user navigates away from the app, but NOT when the phone
        // screen is turned off with Apollo in foreground. For that, we use `lockWhenDisplayIsOff`.
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            lockManager.autoSetLockAfterDelay();
        }
    }

    private void initializeStaticSingletons() {
        Globals.INSTANCE = new GlobalsImpl();
        UserFacingErrorMessages.INSTANCE = new UserFacingErrorMessagesImpl(this);
    }

    /**
     * TODO: should be done off the main thread.
     */
    private void initializeDagger() {
        applicationComponent = DaggerApplicationComponent.builder()
                .dataComponent(getDataComponent())
                .build();

        applicationComponent.inject(this);
    }

    private void initializeLibwallet() {
        libwalletDataDirectory.ensureExists();
        final String filesDir = libwalletDataDirectory.getPath().getAbsolutePath();
        LibwalletBridge.init(filesDir, featuresRepository);
    }

    /**
     * Migrates the shared preferences schema.
     */
    private void migrateSharedPreferences() {
        preferencesMigrationManager.migrate();
    }

    /**
     * TODO: this should probably be called every N minutes.
     *
     * <p><a href="https://productforums.google.com/forum/#!msg/nexus/fslYqYrULto/lU2D3Qe1mugJ">...</a>
     *
     * <p><a href="https://github.com/schwabe/ics-openvpn/issues/246">...</a>
     */
    private void gcmKeepAlive() {
        this.sendBroadcast(new Intent("com.google.android.intent.action.GTALK_HEARTBEAT"));
        this.sendBroadcast(new Intent("com.google.android.intent.action.MCS_HEARTBEAT"));
    }

    /**
     * TODO: should be done off the main Thread.
     */
    private void ensureCurrencyServicesLoaded() {
        // This method eagerly loads the CurrencyProviderSpi instances available in the application
        // space, instead of letting the ServiceLoader create them lazily as needed.

        // This fixes an occasional UnknownCurrencyException that would randomly crash the app
        // on startup, possibly (never fully understood it) due to a timing issue with the provider.

        final ServiceLoader<CurrencyProviderSpi> loader = ServiceLoader
                .load(CurrencyProviderSpi.class);

        final Iterator<CurrencyProviderSpi> it = loader.iterator();

        //noinspection WhileLoopReplaceableByForEach
        while (it.hasNext()) {
            it.next(); // nothing else to do, just populate the ServiceLoader cache.
        }
    }

    /**
     * Returns the Dagger component associated with the Data layer.
     */
    @NotNull
    public DataComponent getDataComponent() {

        if (dataComponent == null) {
            final DataModule dataModule = new DataModule(
                    this,
                    (appContext, transformerFactory, repoRegistry) -> new NotificationServiceImpl(
                            appContext,
                            transformerFactory,
                            new BitcoinUnitSelector(new UserRepository(this, repoRegistry))
                    ),
                    AppStandbyBucketProviderImpl::new,
                    new HoustonConfigImpl()
            );

            dataComponent = DaggerDataComponent.builder()
                    .dataModule(dataModule)
                    .build();
        }

        return dataComponent;
    }

    /**
     * Returns the Dagger component associated with the Application.
     */
    @NotNull
    public ApplicationComponent getApplicationComponent() {
        return applicationComponent;
    }

    @Override
    public void onLowMemory() {
        Timber.i("Application#onLowMemory");
        super.onLowMemory();
    }

    @Override
    public void onTerminate() {
        Timber.i("Application#onTerminate");
        super.onTerminate();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) { // app moved to foreground
        analytics.report(new AnalyticsEvent.E_APP_WILL_ENTER_FOREGROUND());
        backgroundTimesService.enterForeground();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) { // app moved to background
        analytics.report(new AnalyticsEvent.E_APP_WILL_GO_TO_BACKGROUND());
        backgroundTimesService.enterBackground();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) { // app will terminate
        analytics.report(new AnalyticsEvent.E_APP_WILL_TERMINATE());
    }

    private final BroadcastReceiver lockWhenDisplayIsOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(Intent.ACTION_SCREEN_OFF) && lockManager.isLockConfigured()) {
                lockManager.setLock();
            }
        }
    };
}
