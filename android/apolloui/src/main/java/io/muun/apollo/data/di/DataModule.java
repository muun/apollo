package io.muun.apollo.data.di;

import io.muun.apollo.data.afs.ActivityManagerInfoProvider;
import io.muun.apollo.data.afs.AppInfoProvider;
import io.muun.apollo.data.afs.BatteryInfoProvider;
import io.muun.apollo.data.afs.BuildInfoProvider;
import io.muun.apollo.data.afs.ConnectivityInfoProvider;
import io.muun.apollo.data.afs.CpuInfoProvider;
import io.muun.apollo.data.afs.DateTimeZoneProvider;
import io.muun.apollo.data.afs.FileInfoProvider;
import io.muun.apollo.data.afs.HardwareCapabilitiesProvider;
import io.muun.apollo.data.afs.LocaleInfoProvider;
import io.muun.apollo.data.afs.MetricsProvider;
import io.muun.apollo.data.afs.NfcProvider;
import io.muun.apollo.data.afs.PackageManagerInfoProvider;
import io.muun.apollo.data.afs.ResourcesInfoProvider;
import io.muun.apollo.data.afs.SystemCapabilitiesProvider;
import io.muun.apollo.data.afs.SystemInfoProvider;
import io.muun.apollo.data.afs.TelephonyInfoProvider;
import io.muun.apollo.data.afs.TrafficStatsInfoProvider;
import io.muun.apollo.data.apis.DriveAuthenticator;
import io.muun.apollo.data.apis.DriveImpl;
import io.muun.apollo.data.apis.DriveUploader;
import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.incoming_swap.IncomingSwapDao;
import io.muun.apollo.data.db.incoming_swap.IncomingSwapHtlcDao;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.phone_contact.PhoneContactDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.db.submarine_swap.SubmarineSwapDao;
import io.muun.apollo.data.external.AppStandbyBucketProvider;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.external.HoustonConfig;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.fs.LibwalletDataDirectory;
import io.muun.apollo.data.libwallet.HttpClientSessionProvider;
import io.muun.apollo.data.libwallet.KeyProvider;
import io.muun.apollo.data.libwallet.grpc.GrpcChannelFactory;
import io.muun.apollo.data.net.NetworkInfoProvider;
import io.muun.apollo.data.nfc.AndroidNfcBridge;
import io.muun.apollo.data.os.Configuration;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.os.execution.JobExecutor;
import io.muun.apollo.data.preferences.BackgroundTimesRepository;
import io.muun.apollo.data.preferences.FeaturesRepository;
import io.muun.apollo.data.preferences.RepositoryRegistry;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.NotificationPoller;
import io.muun.apollo.domain.libwallet.GoLibwalletService;
import io.muun.apollo.domain.libwallet.LibwalletLogAdapter;
import io.muun.apollo.domain.libwallet.LibwalletService;
import io.muun.apollo.domain.libwallet.WalletClient;

import android.content.Context;
import app_provided_data.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
import io.grpc.ManagedChannel;
import io.reactivex.schedulers.Schedulers;
import org.bitcoinj.core.NetworkParameters;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.functions.Func3;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class DataModule {

    private final Context applicationContext;

    private final Func3<
            Context,
            ExecutionTransformerFactory,
            UserRepository,
            NotificationService
            >
            notificationServiceFactory;

    private final Func1<Context, AppStandbyBucketProvider> appStandbyBucketProviderFactory;

    private final HoustonConfig houstonConfig;

    /**
     * Creates a data module.
     */
    public DataModule(
            Context applicationContext,
            Func3<
                    Context,
                    ExecutionTransformerFactory,
                    UserRepository,
                    NotificationService
                    > notificationServiceFactory,
            Func1<Context, AppStandbyBucketProvider> appStandbyBucketProviderFactory,
            HoustonConfig houstonConfig
    ) {

        this.applicationContext = applicationContext;
        this.notificationServiceFactory = notificationServiceFactory;
        this.appStandbyBucketProviderFactory = appStandbyBucketProviderFactory;
        this.houstonConfig = houstonConfig;
    }

    @Provides
    @Singleton
    Context provideContext() {
        return applicationContext;
    }

    @Provides
    @Singleton
    DataModule provideDataModule() {
        return this;
    }

    /**
     * Provide a dao manager with logging enabled.
     */
    @Provides
    @Singleton
    DaoManager provideDaoManager(
            Context context,
            ContactDao contactDao,
            OperationDao operationDao,
            PhoneContactDao phoneContactDao,
            PublicProfileDao publicProfileDao,
            SubmarineSwapDao submarineSwapDao,
            IncomingSwapDao incomingSwapDao,
            IncomingSwapHtlcDao incomingSwapHtlcDao,
            Configuration config,
            Executor executor
    ) {

        return new DaoManager(
                context,
                config.getString("database.filename"),
                config.getInt("database.version"),
                Schedulers.from(executor),
                contactDao,
                operationDao,
                phoneContactDao,
                publicProfileDao,
                submarineSwapDao,
                incomingSwapHtlcDao,
                incomingSwapDao
        );
    }

    @Provides
    @Singleton
    NotificationService provideNotificationService(
            Context context,
            ExecutionTransformerFactory executionTransformerFactory,
            UserRepository userRepository
    ) {
        return notificationServiceFactory.call(
                context,
                executionTransformerFactory,
                userRepository
        );
    }

    @Provides
    @Singleton
    NetworkParameters provideNetworkParameters() {
        return houstonConfig.getNetwork();
    }

    @Provides
    @Singleton
    Executor provideExecutor(JobExecutor jobExecutor) {
        return jobExecutor;
    }

    @Provides
    @Singleton
    @Named("mainThreadScheduler")
    Scheduler provideScheduler() {
        return AndroidSchedulers.mainThread();
    }

    @Provides
    @Singleton
    @Named("notificationScheduler")
    Scheduler provideNotificationScheduler() {
        return rx.schedulers.Schedulers.from(Executors.newSingleThreadScheduledExecutor(r ->
                new Thread(r, "notifications")
        ));
    }

    @Provides
    @Singleton
    ObjectMapper provideObjectMapper() {
        return new ObjectMapper();
    }

    @Provides
    @Singleton
    HoustonConfig provideHoustonConfig() {
        return houstonConfig;
    }

    @Provides
    @Singleton
    DriveAuthenticator provideGoogleDriveAuthenticator(DriveImpl singletonImpl) {
        return singletonImpl;
    }

    @Provides
    @Singleton
    DriveUploader provideGoogleDriveUploader(DriveImpl singletonImpl) {
        return singletonImpl;
    }

    @Provides
    @Singleton
    AppStandbyBucketProvider provideAppStandbyBucketProvider(final Context context) {
        return appStandbyBucketProviderFactory.call(context);
    }

    @Provides
    @Singleton
    RepositoryRegistry provideRepositoryRegistry() {
        return new RepositoryRegistry();
    }

    // TODO: this should be extracted to a DomainModule or similar
    @Provides
    @Singleton
    NotificationPoller provideNotificationPoller(final NotificationActions notificationActions) {
        return notificationActions;
    }

    @Provides
    @Singleton
    Config provideLibwalletConfig(
            final LibwalletDataDirectory libwalletDataDirectory,
            final FeaturesRepository featuresRepository,
            final HttpClientSessionProvider httpClientSessionProvider,
            final AndroidNfcBridge nfcBridge,
            final KeyProvider keyProvider
    ) {
        libwalletDataDirectory.ensureExists();
        final String dataDir = libwalletDataDirectory.getPath().getAbsolutePath();
        final String socketName = libwalletDataDirectory.getSocket().getAbsolutePath();

        final Config config = new Config();
        config.setDataDir(dataDir);
        config.setSocketPath(socketName);
        config.setFeatureStatusProvider(featuresRepository);
        config.setAppLogSink(new LibwalletLogAdapter());
        config.setHttpClientSessionProvider(httpClientSessionProvider);
        config.setNfcBridge(nfcBridge);
        config.setKeyProvider(keyProvider);

        final String network;
        switch (Globals.INSTANCE.getNetwork().getId()) {
            case NetworkParameters.ID_MAINNET:
                network = "mainnet";
                break;
            case NetworkParameters.ID_REGTEST:
                network = "regtest";
                break;
            case NetworkParameters.ID_TESTNET:
                network = "testnet3";
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown network id " + Globals.INSTANCE.getNetwork().getId()
                );
        }

        config.setNetwork(network);

        return config;
    }

    @Provides
    @Singleton
    ManagedChannel provideGrpcChannel(Config libwalletConfig) {
        final String socketPath = libwalletConfig.getSocketPath();
        return GrpcChannelFactory.create(socketPath);
    }

    @Provides
    @Singleton
    WalletClient provideWalletClient(ManagedChannel channel) {
        return new WalletClient(channel);
    }

    @Provides
    @Singleton
    AndroidNfcBridge provideNfcBridge() {
        return new AndroidNfcBridge();
    }

    @Provides
    LibwalletService provideLibwalletService() {
        return new GoLibwalletService();
    }

    @Provides
    @Singleton
    MetricsProvider provideMetricsProvider(Context context) {
        final ActivityManagerInfoProvider activityManagerInfoProvider =
                new ActivityManagerInfoProvider(context);
        final TelephonyInfoProvider telephonyInfoProvider = new TelephonyInfoProvider(context);
        final HardwareCapabilitiesProvider hardwareCapabilitiesProvider =
                new HardwareCapabilitiesProvider(context);
        final PackageManagerInfoProvider packageManagerInfoProvider =
                new PackageManagerInfoProvider(context);
        final CpuInfoProvider cpuInfoProvider = new CpuInfoProvider();
        final BuildInfoProvider buildInfoProvider = new BuildInfoProvider();
        final FileInfoProvider fileInfoProvider = new FileInfoProvider(context);
        final SystemCapabilitiesProvider systemCapabilitiesProvider =
                new SystemCapabilitiesProvider(context);
        final BackgroundTimesRepository backgroundTimesRepository =
                new BackgroundTimesRepository(context, provideRepositoryRegistry());
        final AppInfoProvider appInfoProvider =
                new AppInfoProvider(context, backgroundTimesRepository);
        final ConnectivityInfoProvider connectivityInfoProvider =
                new ConnectivityInfoProvider(context);
        final ResourcesInfoProvider resourcesInfoProvider = new ResourcesInfoProvider(context);
        final DateTimeZoneProvider dateTimeZoneProvider = new DateTimeZoneProvider(context);
        final LocaleInfoProvider localeInfoProvider = new LocaleInfoProvider();
        final TrafficStatsInfoProvider trafficStatsInfoProvider = new TrafficStatsInfoProvider();
        final NfcProvider nfcProvider = new NfcProvider(context);
        final BatteryInfoProvider batteryInfoProvider = new BatteryInfoProvider(context);
        final SystemInfoProvider systemInfoProvider = new SystemInfoProvider();
        final NetworkInfoProvider networkInfoProvider = new NetworkInfoProvider(context);

        return new MetricsProvider(
                activityManagerInfoProvider,
                telephonyInfoProvider,
                hardwareCapabilitiesProvider,
                packageManagerInfoProvider,
                cpuInfoProvider,
                buildInfoProvider,
                fileInfoProvider,
                systemCapabilitiesProvider,
                appInfoProvider,
                connectivityInfoProvider,
                resourcesInfoProvider,
                dateTimeZoneProvider,
                localeInfoProvider,
                trafficStatsInfoProvider,
                nfcProvider,
                batteryInfoProvider,
                systemInfoProvider,
                networkInfoProvider
        );
    }
}
