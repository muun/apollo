package io.muun.apollo.data.preferences.migration;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.BaseRepository;
import io.muun.apollo.data.preferences.ClientVersionRepository;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.NotificationRepository;
import io.muun.apollo.data.preferences.SchemaVersionRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;

import android.content.Context;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

/**
 * Migrates the shared preferences, running all the not-already-run migrations, storing in a shared
 * preference the index of the last one run.
 */
public class PreferencesMigrationManager {

    private final Context context;
    private final SchemaVersionRepository schemaVersionRepository;
    private final AuthRepository authRepository;

    private final List<BaseRepository> repositoriesToClear;
    private final List<String> thirdPartyPreferencesToClear;

    private final UserRepository userRepository;

    /**
     * An array of migrations, in the order that they must be run.
     */
    private final Migration[] migrations = {
            () -> {
            },
            this::clearAllRepositories,
            this::clearAllRepositories,
            this::clearAllRepositories,
            this::clearAllRepositories,

            // nov 2017, first implementation of the device secure storage, we need to also delete
            // the old storage, as we also removed the handler class, we need to do it explicitly.
            this::clearAllRepositories,

            // feb 2018, Second implementation of secure storage, we are removing the
            // authentication, so we need to wipe keystore and logout the user.
            this::clearAllRepositories,

            this::moveJwtKeyToSecureStorage,

            this::clearSignupDraft
    };

    /**
     * Creates migration manager.
     */
    @Inject
    public PreferencesMigrationManager(Context context,
                                       AuthRepository authRepository,
                                       ExchangeRateWindowRepository exchangeRateWindowRepository,
                                       KeysRepository keysRepository,
                                       UserRepository userRepository,
                                       SchemaVersionRepository schemaVersionRepository,
                                       FeeWindowRepository feeWindowRepository,
                                       ClientVersionRepository clientVersionRepository,
                                       NotificationRepository notificationRepository,
                                       TransactionSizeRepository transactionSizeRepository) {
        this.context = context;
        this.schemaVersionRepository = schemaVersionRepository;

        this.repositoriesToClear = Arrays.asList(
                authRepository,
                exchangeRateWindowRepository,
                keysRepository,
                userRepository,
                schemaVersionRepository,
                feeWindowRepository,
                clientVersionRepository,
                notificationRepository,
                transactionSizeRepository
        );

        this.userRepository = userRepository;

        this.thirdPartyPreferencesToClear = createThirdPartyPreferencesList();
        this.authRepository = authRepository;
    }

    /**
     * Updates the schema to the current version.
     */
    public void migrate() {

        if (!schemaVersionRepository.hasVersion()) {

            schemaVersionRepository.setVersion(migrations.length - 1);
            return;
        }

        final int currentVersion = schemaVersionRepository.getVersion();

        for (int version = currentVersion + 1; version < migrations.length; version++) {
            Logger.info("Running shared preferences' migration %s...", version);
            migrations[version].run();
            schemaVersionRepository.setVersion(version);
        }
    }

    /**
     * Destroys information in all SharedPreferences repositories.
     * @implNote  schemaVersionRepository will also be wiped.
     */
    public void clearAllRepositories() {
        for (BaseRepository repository : repositoriesToClear) {
            try {
                repository.clear();
            } catch (Exception e) {
                Logger.error(e);
            }
        }

        for (String fileName : thirdPartyPreferencesToClear) {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply();
        }
    }

    /**
     * Destroys information for SignupDraft.
     */
    public void clearSignupDraft() {
        userRepository.clearSignupDraft();
    }

    private void moveJwtKeyToSecureStorage() {
        authRepository.moveJwtToSecureStorage();
    }

    private List<String> createThirdPartyPreferencesList() {
        // NOTE: there is no reliable way of listing all SharedPreferences. The XML files are
        // *usually* located in a known directory, but some vendors change this. We cannot control
        // where the directory lies in a particular device, but we can know and control the actual
        // preferences created by Apollo's dependencies.

        // The following list was created by logging into Apollo, and listing the XML files created
        // in the data/shared_prefs folder of the application.

        // ON-RELEASE verify that the list matches the actual files added by our dependencies. This
        // won't crash if files do not exist, but we could miss some.
        return Arrays.asList(
                "TwitterAdvertisingInfoPreferences",
                "WebViewChromiumPrefs",
                "com.crashlytics.prefs",
                "com.crashlytics.sdk.android:answers:settings",
                "com.crashlytics.sdk.android.crashlytics-core"
                        + ":com.crashlytics.android.core.CrashlyticsCore",

                "com.google.android.gms.appid",
                "com.google.android.gms.measurement.prefs",
                "io.fabric.sdk.android:fabric:io.fabric.sdk.android.Onboarding"
        );
    }
}
