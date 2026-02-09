package io.muun.apollo.presentation.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import app_provided_data.Config
import com.google.firebase.analytics.FirebaseAnalytics
import com.jakewharton.threetenabp.AndroidThreeTen
import io.muun.apollo.data.async.tasks.MuunWorkerFactory
import io.muun.apollo.data.debug.HeapDumper
import io.muun.apollo.data.di.DaggerDataComponent
import io.muun.apollo.data.di.DataComponent
import io.muun.apollo.data.di.DataModule
import io.muun.apollo.data.external.DataComponentProvider
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.data.logging.Crashlytics
import io.muun.apollo.data.logging.LoggingContext
import io.muun.apollo.data.logging.MuunTree
import io.muun.apollo.data.preferences.migration.PreferencesMigrationManager
import io.muun.apollo.domain.ApplicationLockManager
import io.muun.apollo.domain.BackgroundTimesProcessor
import io.muun.apollo.domain.NightModeManager
import io.muun.apollo.domain.action.UserActions
import io.muun.apollo.domain.action.realtime.PreloadFeeDataAction
import io.muun.apollo.domain.action.session.DetectAppUpdateAction
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_APP_WILL_ENTER_FOREGROUND
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_APP_WILL_GO_TO_BACKGROUND
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_APP_WILL_TERMINATE
import io.muun.apollo.domain.libwallet.LibwalletBridge
import io.muun.apollo.domain.model.NightMode
import io.muun.apollo.domain.model.NightMode.DARK
import io.muun.apollo.domain.model.NightMode.FOLLOW_SYSTEM
import io.muun.apollo.domain.model.NightMode.LIGHT
import io.muun.apollo.domain.model.feebump.FeeBumpRefreshPolicy
import io.muun.apollo.domain.sync.FeeDataSyncer
import io.muun.apollo.presentation.app.di.ApplicationComponent
import io.muun.apollo.presentation.app.di.DaggerApplicationComponent
import io.muun.apollo.presentation.app.startup.AppStartupInitializer
import io.muun.apollo.presentation.ui.utils.UserFacingErrorMessagesImpl
import rx.plugins.RxJavaHooks
import timber.log.Timber
import java.util.ServiceLoader
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.money.spi.CurrencyProviderSpi

open class ApolloApplication : Application(), DataComponentProvider, Configuration.Provider,
    DefaultLifecycleObserver {

    private var applicationComponent: ApplicationComponent? = null

    private var dataComponent: DataComponent? = null

    @Inject
    lateinit var backgroundExecutor: Executor

    @Inject
    lateinit var preferencesMigrationManager: PreferencesMigrationManager

    @Inject
    lateinit var analytics: Analytics

    @Inject
    lateinit var lockManager: ApplicationLockManager

    @Inject
    lateinit var libwalletConfig: Config

    @Inject
    lateinit var nightModeManager: NightModeManager

    @Inject
    lateinit var detectAppUpdate: DetectAppUpdateAction

    @Inject
    lateinit var backgroundTimesProcessor: BackgroundTimesProcessor

    @Inject
    lateinit var preloadFeeData: PreloadFeeDataAction

    @Inject
    lateinit var feeDataSyncer: FeeDataSyncer

    @Inject
    lateinit var userActions: UserActions

    @Inject
    lateinit var appStartupInitializer: AppStartupInitializer

    override fun onCreate() {
        // The order of the calls in this method is intentional.
        // Please don't rearrange them without understanding the implications.

        super<Application>.onCreate()

        initializeStaticSingletons()

        HeapDumper.init(this)
        Crashlytics.init(this)
        StrictMode.init()

        ensureCurrencyServicesLoaded()

        AndroidThreeTen.init(this)

        // Ignore tracking events for Firebase Test Lab devices
        if (isFirebaseTestLabDevice()) {
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)
        }

        setupDebugTools()
        initializeDagger()
        appStartupInitializer.init().await()

        detectAppUpdate.run()

        initializeLibwallet()

        migrateSharedPreferences()

        // Must run after the prefs migration.
        setNightMode()

        gcmKeepAlive()

        loadBigQueryPseudoId()

        // Register lifecycle observer for app background/foreground/terminate tracking
        // Apparently this won't correctly handle/track app crashes or ANRs.
        // See: https://stackoverflow.com/a/44461605/901465 (and its comments)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        registerReceiver(lockWhenDisplayIsOff, IntentFilter(Intent.ACTION_SCREEN_OFF))

        RxJavaHooks.enableAssemblyTracking()

        if (lockManager.isLockConfigured()) {
            lockManager.setLock()
        }
    }

    private fun loadBigQueryPseudoId() {
        analytics.loadBigQueryPseudoId()
    }

    private fun setNightMode() {
        val nightMode: NightMode = nightModeManager.get()

        when (nightMode) {
            DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            FOLLOW_SYSTEM -> Unit
        }
    }

    /**
     * <pre>
     * See [WorkManager docs](https://developer.android.com/topic/libraries/architecture/workmanager/
      advanced/custom-configuration#implement-configuration-provider).
    </pre> *
     * Shortened URL: [...](https://shorturl.at/aflsA)
     */
    override val workManagerConfiguration: Configuration by lazy {
        Timber.d("[MuunWorkerFactory] Application#getWorkManagerConfiguration()")
        val loggingLevel = if (Globals.INSTANCE.isRelease) Log.ERROR else Log.VERBOSE
        Configuration.Builder()
            .setWorkerFactory(MuunWorkerFactory(this))
            .setMinimumLoggingLevel(loggingLevel)
            .build()
    }

    /**
     * Detect Google Play Pre-Launch report automatic tests, by identifying Firebase Test Lab
     * devices, to ignore tracking events from them.
     * See:
     * [...](https://firebase.google.com/docs/test-lab/android/android-studio)
     * [...](https://stackoverflow.com/a/45070039/901465)
     */
    private fun isFirebaseTestLabDevice(): Boolean {
        return "true" == Settings.System.getString(getContentResolver(), "firebase.test.lab")
    }

    /**
     * This configures logging and debugging utilities. It has an override in ApolloDebugApplication
     * for additional tooling.
     */
    @CallSuper
    protected open fun setupDebugTools() {
        Timber.plant(MuunTree())

        val isDebug = Globals.INSTANCE.isDebug
        val isDogfood = Globals.INSTANCE.isDogfood

        // Use logcat only for debug builds:
        LoggingContext.sendToLogcat = isDebug || isDogfood

        // Use Crashlytics always:
        LoggingContext.sendToCrashlytics = true
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // NOTE: this triggers when the user navigates away from the app, but NOT when the phone
        // screen is turned off with Apollo in foreground. For that, we use `lockWhenDisplayIsOff`.
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            lockManager.autoSetLockAfterDelay()
        }
    }

    private fun initializeStaticSingletons() {
        Globals.INSTANCE = GlobalsImpl()
        UserFacingErrorMessages.INSTANCE = UserFacingErrorMessagesImpl(this)
    }

    /**
     * TODO: should be done off the main thread.
     */
    private fun initializeDagger() {
        applicationComponent = DaggerApplicationComponent.builder()
            .dataComponent(getDataComponent())
            .build()

        getApplicationComponent().inject(this)
    }

    private fun initializeLibwallet() {
        LibwalletBridge.init(libwalletConfig)
        LibwalletBridge.startServer()
    }

    /**
     * Migrates the shared preferences schema.
     */
    private fun migrateSharedPreferences() {
        preferencesMigrationManager.migrate()
    }

    /**
     * TODO: this should probably be called every N minutes.
     * [...](https://productforums.google.com/forum/#!msg/nexus/fslYqYrULto/lU2D3Qe1mugJ)
     * [...](https://github.com/schwabe/ics-openvpn/issues/246)
     */
    private fun gcmKeepAlive() {
        sendBroadcast(Intent("com.google.android.intent.action.GTALK_HEARTBEAT"))
        sendBroadcast(Intent("com.google.android.intent.action.MCS_HEARTBEAT"))
    }

    /**
     * TODO: should be done off the main Thread.
     */
    private fun ensureCurrencyServicesLoaded() {
        // This method eagerly loads the CurrencyProviderSpi instances available in the application
        // space, instead of letting the ServiceLoader create them lazily as needed.

        // This fixes an occasional UnknownCurrencyException that would randomly crash the app
        // on startup, possibly (never fully understood it) due to a timing issue with the provider.

        val loader = ServiceLoader.load(CurrencyProviderSpi::class.java)

        val it = loader.iterator()

        while (it.hasNext()) {
            it.next() // nothing else to do, just populate the ServiceLoader cache.
        }
    }

    /**
     * Returns the Dagger component associated with the Data layer.
     */
    override fun getDataComponent(): DataComponent {
        if (dataComponent == null) {
            val dataModule = DataModule(this, HoustonConfigImpl())

            dataComponent = DaggerDataComponent.builder()
                .dataModule(dataModule)
                .build()
        }

        return dataComponent!!
    }

    /**
     * Returns the Dagger component associated with the Application.
     */
    fun getApplicationComponent(): ApplicationComponent {
        return applicationComponent!!
    }

    override fun onLowMemory() {
        Timber.i("Application#onLowMemory")
        super.onLowMemory()
    }

    override fun onTerminate() {
        Timber.i("Application#onTerminate")
        super.onTerminate()
    }

    override fun onStart(owner: LifecycleOwner) { // app moved to foreground
        analytics.report(E_APP_WILL_ENTER_FOREGROUND())
        backgroundTimesProcessor.enterForeground()

        if (userActions.isLoggedIn()) {
            preloadFeeData.run(FeeBumpRefreshPolicy.FOREGROUND)
            feeDataSyncer.enterForeground()
        }
    }

    override fun onStop(owner: LifecycleOwner) { // app moved to background
        analytics.report(E_APP_WILL_GO_TO_BACKGROUND())
        backgroundTimesProcessor.enterBackground()
        feeDataSyncer.enterBackground()
    }

    override fun onDestroy(owner: LifecycleOwner) { // app will terminate
        getDataComponent().libwalletClient().shutdown()
        LibwalletBridge.stopServer()
        analytics.report(E_APP_WILL_TERMINATE())
    }

    private val lockWhenDisplayIsOff: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            backgroundExecutor.execute {
                val action = intent.action
                if (action == Intent.ACTION_SCREEN_OFF && lockManager.isLockConfigured) {
                    lockManager.setLock()
                }
            }
        }
    }
}