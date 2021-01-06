package io.muun.apollo.data.di;

import io.muun.apollo.data.apis.DriveAuthenticator;
import io.muun.apollo.data.apis.DriveUploader;
import io.muun.apollo.data.async.gcm.GcmMessageListenerService;
import io.muun.apollo.data.async.tasks.PeriodicTaskWorkerFactory;
import io.muun.apollo.data.async.tasks.TaskDispatcher;
import io.muun.apollo.data.async.tasks.TaskScheduler;
import io.muun.apollo.data.db.DaoManager;
import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.operation.OperationDao;
import io.muun.apollo.data.db.phone_contact.PhoneContactDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.external.HoustonConfig;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.net.ModelObjectsMapper;
import io.muun.apollo.data.net.NetworkInfoProvider;
import io.muun.apollo.data.os.ClipboardProvider;
import io.muun.apollo.data.os.Configuration;
import io.muun.apollo.data.os.ContactsProvider;
import io.muun.apollo.data.os.TelephonyInfoProvider;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.os.secure_storage.KeyStoreProvider;
import io.muun.apollo.data.os.secure_storage.SecureStoragePreferences;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FcmTokenRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.SignupDraftManager;
import io.muun.apollo.domain.action.LogoutActions;
import io.muun.apollo.domain.action.di.ActionComponent;

import android.content.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Component;
import org.bitcoinj.core.NetworkParameters;

import javax.inject.Singleton;

@Singleton
@Component(modules = DataModule.class)
public interface DataComponent extends ActionComponent {

    void inject(GcmMessageListenerService service);

    void inject(PeriodicTaskWorkerFactory workerFactory);

    // Exposed to dependent components

    ExecutionTransformerFactory transformers();

    TelephonyInfoProvider telephonyProvider();

    HoustonClient houstonClient();

    DataModule dataModule();

    AuthRepository authRepository();

    KeysRepository keysRepository();

    UserRepository userRepository();

    FcmTokenRepository fcmTokenRepository();

    ContactDao contactDao();

    OperationDao operationDao();

    PublicProfileDao publicProfileDao();

    DaoManager daoManager();

    NetworkParameters networkParameters();

    TaskDispatcher taskDispatcher();

    TaskScheduler taskScheduler();

    ExchangeRateWindowRepository exchangeRateWindowRepository();

    ClipboardProvider clipboardProvider();

    ContactsProvider contactsProvider();

    NetworkInfoProvider networkInfoProvider();

    PhoneContactDao phoneContactDao();

    Context context();

    FeeWindowRepository expectedFeeRepository();

    Configuration configuration();

    SecureStoragePreferences secureStoragePreferences();

    KeyStoreProvider keyStoreProvider();

    SecureStorageProvider secureStorageProvider();

    LogoutActions logoutActions();

    ApplicationLockManager applicationLockManager();

    SignupDraftManager signupDraftManager();

    ObjectMapper objectMapper();

    HoustonConfig houstonConfig();

    ModelObjectsMapper modelObjectsMapper();

    DriveAuthenticator driveAuthenticator();

    DriveUploader driveUploader();
}
