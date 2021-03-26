package io.muun.apollo.presentation.app;

import io.muun.apollo.data.async.tasks.PeriodicTaskWorkerFactory;
import io.muun.apollo.data.di.DaggerDataComponent;
import io.muun.apollo.data.di.DataComponent;
import io.muun.apollo.data.di.DataModule;
import io.muun.apollo.data.external.DataComponentProvider;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.external.UserFacingErrorMessages;
import io.muun.apollo.data.fs.LibwalletDataDirectory;
import io.muun.apollo.data.logging.LoggingContext;
import io.muun.apollo.data.logging.MuunTree;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.data.preferences.migration.PreferencesMigrationManager;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.NightModeManager;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.NightMode;
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector;
import io.muun.apollo.presentation.analytics.Analytics;
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
        implements DataComponentProvider, Configuration.Provider {

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

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ensureCurrencyServicesLoaded();

        AndroidThreeTen.init(this);

        // Ignore tracking events for Firebase Test Lab devices
        if (isFirebaseTestLabDevice()) {
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false);
        }

        initializeStaticSingletons();
        initializeDagger();

        setNightMode();

        initializeLibwallet();

        setupDebugTools();

        migrateSharedPreferences();

        gcmKeepAlive();

        // Register lifecycle observer for app background/foreground/terminate tracking
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppLifecycleListener(analytics));

        registerReceiver(lockWhenDisplayIsOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        RxJavaHooks.enableAssemblyTracking();

        if (lockManager.isLockConfigured()) {
            lockManager.setLock();
        }
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
     * advanced/custom-configuration#implement-configuration-provider">WorkManager docs</a>
     * </pre>
     * Shortened URL: https://shorturl.at/aflsA
     */
    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(new PeriodicTaskWorkerFactory(this))
                .setMinimumLoggingLevel(Globals.isReleaseBuild() ? Log.ASSERT : Log.VERBOSE)
                .build();
    }

    /**
     * Detect Google Play Pre-Launch report automatic tests, by identifying Firebase Test Lab
     * devices, to ignore tracking events from them.
     * See:
     * https://firebase.google.com/docs/test-lab/android/android-studio
     * https://stackoverflow.com/a/45070039/901465
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

    private void initializeDagger() {
        applicationComponent = DaggerApplicationComponent.builder()
                .dataComponent(getDataComponent())
                .build();

        applicationComponent.inject(this);
    }

    private void initializeLibwallet() {
        libwalletDataDirectory.ensureExists();
        final String filesDir = libwalletDataDirectory.getPath().getAbsolutePath();
        LibwalletBridge.init(filesDir);
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
     * <p>https://productforums.google.com/forum/#!msg/nexus/fslYqYrULto/lU2D3Qe1mugJ
     *
     * <p>https://github.com/schwabe/ics-openvpn/issues/246
     */
    private void gcmKeepAlive() {
        this.sendBroadcast(new Intent("com.google.android.intent.action.GTALK_HEARTBEAT"));
        this.sendBroadcast(new Intent("com.google.android.intent.action.MCS_HEARTBEAT"));
    }

    private void ensureCurrencyServicesLoaded() {
        // This method eagerly loads the CurrencyProviderSpi instances available in the application
        // space, instead of letting the ServiceLoader create them lazily as needed.

        // This fixes an occasional UnknownCurrencyException that would randomly crash the app
        // on startup, possibly (never fully understood it) due to a timing issue with the provider.

        final ServiceLoader<CurrencyProviderSpi> loader = ServiceLoader
                .load(CurrencyProviderSpi.class);

        final Iterator<CurrencyProviderSpi> it = loader.iterator();

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
                            new CurrencyDisplayModeSelector(new UserRepository(this, repoRegistry))
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
        super.onLowMemory();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
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
