package io.muun.apollo.data.di;

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
import io.muun.apollo.data.external.HoustonConfig;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.os.Configuration;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.os.execution.JobExecutor;
import io.muun.apollo.data.preferences.RepositoryRegistry;
import io.muun.apollo.domain.action.NotificationActions;
import io.muun.apollo.domain.action.NotificationPoller;

import android.content.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
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

    private final Func3<Context, ExecutionTransformerFactory, RepositoryRegistry, NotificationService>
            notificationServiceFactory;

    private final Func1<Context, AppStandbyBucketProvider> appStandbyBucketProviderFactory;

    private final HoustonConfig houstonConfig;

    /**
     * Creates a data module.
     */
    public DataModule(
            Context applicationContext,
            Func3<Context, ExecutionTransformerFactory, RepositoryRegistry, NotificationService> notificationServiceFactory,
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
            RepositoryRegistry repoRegistry
    ) {
        return notificationServiceFactory.call(context, executionTransformerFactory, repoRegistry);
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
    Scheduler provideScheduler() {
        return AndroidSchedulers.mainThread();
    }

    @Provides
    @Singleton
    @Named("notificationScheduler")
    rx.Scheduler provideNotificationScheduler() {
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
}
