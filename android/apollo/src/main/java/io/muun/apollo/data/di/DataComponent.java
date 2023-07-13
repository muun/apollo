package io.muun.apollo.data.di;

import io.muun.apollo.data.apis.DriveAuthenticator;
import io.muun.apollo.data.apis.DriveUploader;
import io.muun.apollo.data.async.gcm.GcmMessageListenerService;
import io.muun.apollo.data.async.tasks.MuunWorkerFactory;
import io.muun.apollo.data.async.tasks.TaskScheduler;
import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.external.HoustonConfig;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.net.NetworkInfoProvider;
import io.muun.apollo.data.os.ClipboardProvider;
import io.muun.apollo.data.os.Configuration;
import io.muun.apollo.data.os.TelephonyInfoProvider;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.RepositoryRegistry;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.SignupDraftManager;
import io.muun.apollo.domain.action.LogoutActions;
import io.muun.apollo.domain.action.di.ActionComponent;
import io.muun.apollo.domain.analytics.Analytics;

import android.content.Context;
import dagger.Component;
import org.bitcoinj.core.NetworkParameters;

import javax.inject.Singleton;

/**
 * Dagger Component. {@link Component}.
 * Add here:
 * - members-injection methods (e.g for classes which lifecycles are 3rd-party controlled, like
 * Android's). Example: void inject(GcmMessageListenerService service).
 * - provision methods, to expose injected or provided dependencies to other (dependent) components.
 * Example: TelephonyInfoProvider telephonyProvider();
 */
@SuppressWarnings("checkstyle:MissingJavadocMethod")
@Singleton
@Component(modules = DataModule.class)
public interface DataComponent extends ActionComponent {

    void inject(GcmMessageListenerService service);

    void inject(MuunWorkerFactory workerFactory);

    // Exposed to dependent components

    ExecutionTransformerFactory transformers();

    TelephonyInfoProvider telephonyProvider();

    HoustonClient houstonClient();

    AuthRepository authRepository();

    KeysRepository keysRepository();

    UserRepository userRepository();

    FirebaseInstallationIdRepository fcmTokenRepository();

    ContactDao contactDao();

    OperationDao operationDao();

    PublicProfileDao publicProfileDao();

    NetworkParameters networkParameters();

    TaskScheduler taskScheduler();

    ExchangeRateWindowRepository exchangeRateWindowRepository();

    ClipboardProvider clipboardProvider();

    NetworkInfoProvider networkInfoProvider();

    Context context();

    FeeWindowRepository expectedFeeRepository();

    Configuration configuration();

    SecureStorageProvider secureStorageProvider();

    LogoutActions logoutActions();

    ApplicationLockManager applicationLockManager();

    SignupDraftManager signupDraftManager();

    HoustonConfig houstonConfig();

    DriveAuthenticator driveAuthenticator();

    DriveUploader driveUploader();

    RepositoryRegistry repositoryRegistry();

    NotificationService notificationService();

    Analytics analytics();
}
