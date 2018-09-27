package io.muun.apollo.data.di;

import io.muun.apollo.BuildConfig;
import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.phone_contact.PhoneContactDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.os.Configuration;
import io.muun.apollo.data.os.execution.JobExecutor;
import io.muun.apollo.domain.NotificationService;
import io.muun.common.bitcoinj.NetworkParametersHelper;

import android.content.Context;
import dagger.Module;
import dagger.Provides;
import org.bitcoinj.core.NetworkParameters;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class DataModule {

    private final Context applicationContext;

    private final NotificationService notificationService;

    /**
     * Creates a data module.
     */
    public DataModule(Context applicationContext, NotificationService notificationService) {
        this.applicationContext = applicationContext;
        this.notificationService = notificationService;
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
    DaoManager provideDaoManager(Context context, ContactDao contactDao, OperationDao operationDao,
                                 PhoneContactDao phoneContactDao, PublicProfileDao publicProfileDao,
                                 Configuration config, Executor executor) {

        return new DaoManager(
                context,
                config.getString("database.filename"),
                config.getInt("database.version"),
                Schedulers.from(executor),
                contactDao,
                operationDao,
                phoneContactDao,
                publicProfileDao
        );
    }

    @Provides
    @Singleton
    NetworkParameters provideNetworkParameters(Configuration config) {
        return NetworkParametersHelper.getNetworkParametersFromName(BuildConfig.NETWORK_NAME);
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
    NotificationService provideNotificationService() {
        return notificationService;
    }

    /**
     * Provide the URL of houston.
     */
    @Provides
    @Singleton
    @Named("houstonUrl")
    String provideHoustonUrl(Configuration config) {
        String basePath = config.getString("net.serverBasePath");

        if (! basePath.isEmpty()) {
            basePath += "/"; // baseUrl needs a trailing slash
        }

        return String.format(
                "%s://%s:%s/%s",
                config.getString("net.serverProtocol"),
                config.getString("net.serverDomain"),
                config.getString("net.serverPort"),
                basePath
        );
    }
}
