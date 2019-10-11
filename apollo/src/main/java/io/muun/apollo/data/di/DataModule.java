package io.muun.apollo.data.di;

import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.hwallet.HardwareWalletDao;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.phone_contact.PhoneContactDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.db.satellite_pairing.SatellitePairingDao;
import io.muun.apollo.data.db.submarine_swap.SubmarineSwapDao;
import io.muun.apollo.data.os.Configuration;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.os.execution.JobExecutor;
import io.muun.apollo.external.HoustonConfig;
import io.muun.apollo.external.NotificationService;

import android.content.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
import io.reactivex.schedulers.Schedulers;
import org.bitcoinj.core.NetworkParameters;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func2;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

@Module
public class DataModule {

    private final Context applicationContext;

    private final Func2<Context, ExecutionTransformerFactory, NotificationService>
            notificationServiceFactory;

    private final HoustonConfig houstonConfig;

    /**
     * Creates a data module.
     */
    public DataModule(
            Context applicationContext,
            Func2<Context, ExecutionTransformerFactory, NotificationService> factory,
            HoustonConfig houstonConfig) {

        this.applicationContext = applicationContext;
        this.notificationServiceFactory = factory;
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
    DaoManager provideDaoManager(Context context,
                                 ContactDao contactDao,
                                 OperationDao operationDao,
                                 PhoneContactDao phoneContactDao,
                                 PublicProfileDao publicProfileDao,
                                 SatellitePairingDao satellitePairingDao,
                                 HardwareWalletDao hardwareWalletDao,
                                 SubmarineSwapDao submarineSwapDao,
                                 Configuration config,
                                 Executor executor) {

        return new DaoManager(
                context,
                config.getString("database.filename"),
                config.getInt("database.version"),
                Schedulers.from(executor),
                contactDao,
                operationDao,
                phoneContactDao,
                publicProfileDao,
                satellitePairingDao,
                hardwareWalletDao,
                submarineSwapDao
        );
    }

    @Provides
    @Singleton
    NotificationService provideNotificationService(
            Context context,
            ExecutionTransformerFactory executionTransformerFactory) {
        return notificationServiceFactory.call(context, executionTransformerFactory);
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
    ObjectMapper provideObjectMapper() {
        return new ObjectMapper();
    }

    @Provides
    @Singleton
    HoustonConfig provideHoustonConfig() {
        return houstonConfig;
    }
}
