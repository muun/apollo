package io.muun.apollo.data.preferences.migration;

import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.SchemaVersionRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.action.LogoutActions;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.common.model.SessionStatus;

import android.content.Context;
import android.content.SharedPreferences;
import timber.log.Timber;

import java.util.Collections;

import javax.inject.Inject;

/**
 * Migrates the shared preferences, running all the not-already-run migrations, storing in a shared
 * preference the index of the last one run.
 */
public class PreferencesMigrationManager {

    private final Context context;

    private final SchemaVersionRepository schemaVersionRepository;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;

    private final LogoutActions logoutActions;

    private final FeeWindowRepository feeWindowRepository;

    private final TransactionSizeRepository transactionSizeRepository;

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

            this::clearSignupDraft,
            this::logout,

            // jun 2019, implement customizable fee feature, with more than 1 fee rate in FeeWindow
            this::upgradeFeeWindowRepositoryForCustomFees,

            // sep 2019, Apollo 30 replaces SignupDraft with isInitialSyncCompleted
            this::setInitialSyncCompleted,

            // feb 2020, Apollo 38 divides signup and login flows
            this::clearSignupDraft,

            // apr 2020, Apollo 64 rolls out user debt and 0 sat fees low amount  ln payments
            this::initExpectedDebt,

            // may 2020, Apollo 67 uses JsonPreference for User instead of field-level preferences
            this::moveUserToJsonPreference
    };

    /**
     * Creates migration manager.
     */
    @Inject
    public PreferencesMigrationManager(Context context,
                                       AuthRepository authRepository,
                                       SchemaVersionRepository schemaVersionRepository,
                                       UserRepository userRepository,
                                       LogoutActions logoutActions,
                                       FeeWindowRepository feeWindowRepository,
                                       TransactionSizeRepository transactionSizeRepository) {
        this.context = context;

        this.schemaVersionRepository = schemaVersionRepository;

        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.feeWindowRepository = feeWindowRepository;

        this.logoutActions = logoutActions;
        this.transactionSizeRepository = transactionSizeRepository;
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
            Timber.d("Running shared preferences' migration %s...", version);
            migrations[version].run();
            schemaVersionRepository.setVersion(version);
        }
    }

    private void clearAllRepositories() {
        logoutActions.clearAllRepositories();
    }

    private void logout() {
        logoutActions.destroyRecoverableWallet();
    }

    /**
     * Destroys information for SignupDraft.
     */
    private void clearSignupDraft() {
        userRepository.storeSignupDraft(null);
    }

    private void moveJwtKeyToSecureStorage() {
        authRepository.moveJwtToSecureStorage();
    }

    private void upgradeFeeWindowRepositoryForCustomFees() {
        final SharedPreferences prefs = context
                .getSharedPreferences("fee_window", Context.MODE_PRIVATE);

        final String keyHoustonId = "houston_id";
        final String keyFetchDate = "fetch_date";
        final String feeFeeInSatoshisPerByte = "fee_in_satoshis_per_byte";

        if (!prefs.contains(keyHoustonId)) {
            return; // nothing to migrate
        }

        final FeeWindow feeWindow = new FeeWindow(
                prefs.getLong("houston_id", 0),
                SerializationUtils.deserializeDate(prefs.getString(keyFetchDate, "")),
                Collections.singletonMap(1, (double) prefs.getLong(feeFeeInSatoshisPerByte, 0))
        );

        feeWindowRepository.store(feeWindow);

        prefs.edit()
                .remove(keyHoustonId)
                .remove(keyFetchDate)
                .remove(feeFeeInSatoshisPerByte)
                .apply();
    }

    private void setInitialSyncCompleted() {
        final SharedPreferences prefs = context
                .getSharedPreferences("user", Context.MODE_PRIVATE);

        // Before Apollo 30, we considered the initial sync to be completed when the SignupDraft
        // preference was cleared at the end of the signup/login flow. Now, we need to set the
        // boolean that indicates that based on the old mechanism.
        final boolean hasSignupDraft = prefs.contains("signup_draft");

        final boolean isInitialSyncCompleted = authRepository
                .getSessionStatus()
                .map(SessionStatus.LOGGED_IN::equals)
                .map(isLoggedIn -> isLoggedIn && !hasSignupDraft)
                .orElse(false);

        if (isInitialSyncCompleted) {
            userRepository.storeInitialSyncCompleted();
        }
    }

    private void initExpectedDebt() {
        final SharedPreferences prefs = context
                .getSharedPreferences("transaction_size", Context.MODE_PRIVATE);

        final boolean hasNts = prefs.contains("transaction_size");

        if (!hasNts) {
            return;
        }

        transactionSizeRepository.initExpectedDebt();
    }

    private void moveUserToJsonPreference() {
        userRepository.migrateCthulhuToJsonPreference();
    }
}
