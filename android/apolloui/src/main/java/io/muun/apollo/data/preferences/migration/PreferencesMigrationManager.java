package io.muun.apollo.data.preferences.migration;

import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.SchemaVersionRepository;
import io.muun.apollo.data.preferences.TransactionSizeRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter;
import io.muun.apollo.data.preferences.stored.StoredEkVerificationCodes;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.SignupDraftManager;
import io.muun.apollo.domain.action.session.ClearRepositoriesAction;
import io.muun.apollo.domain.action.session.LegacyLogoutUserForMigrationAction;
import io.muun.apollo.domain.libwallet.WalletClient;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.NightMode;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.model.SessionStatus;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import timber.log.Timber;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

/**
 * Migrates the shared preferences, running all the not-already-run migrations, storing in a shared
 * preference the index of the last one run.
 */
public class PreferencesMigrationManager {

    private final Context context;

    private final SignupDraftManager signupDraftManager;
    private final LegacyLogoutUserForMigrationAction legacyLogout;
    private final ClearRepositoriesAction clearRepositories;

    private final SchemaVersionRepository schemaVersionRepository;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final FeeWindowRepository feeWindowRepository;
    private final TransactionSizeRepository transactionSizeRepository;
    private final FirebaseInstallationIdRepository firebaseInstallationIdRepository;
    private final KeysRepository keysRepository;
    private final WalletClient walletClient;

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
            this::moveUserToJsonPreference,

            // jul 2020, Apollo 70 starts using dynamic fee targets
            this::initDynamicFeeTargets,

            // jul 2020, Apollo 70 starts prioritizing confirmed utxos in utxo selection
            this::initNtsOutpoints,

            // sept 2020, Apollo 76 moves FcmToken to its own repository (not cleared on logout)
            this::moveFcmTokenToOwnRepository,

            // sept 2020, Apollo 76 adds ChallengePublicKey version (needed for RC only login,
            // part of the email less recovery feature)
            this::addChallengePublicKeyVersion,

            // Oct 2020, Apollo 76 moves SignupDraft to secureStorage to better secure RC only login
            this::moveSignupDraftToSecureStorage,

            // Nov 2020, Apollo 77 now remembers many Emergency Kit verification codes
            this::migrateToRecentEmergencyKitVerificationCodes,

            // Sept 2021, Apollo 700 introduces EmergencyKit version and its own model
            this::addEmergencyKitVersion,

            // Feb 2024, Apollo 1108 add local migration that we missed in 2020 (when we
            // added UtxoStatus to NTS but never used it).
            this::initNtsUtxoStatus,

            this::moveNightModeToLibwalletStorage,
    };

    /**
     * Creates migration manager.
     */
    @Inject
    public PreferencesMigrationManager(
            Context context,
            SignupDraftManager signupDraftManager,
            LegacyLogoutUserForMigrationAction legacyLogout,
            ClearRepositoriesAction clearRepositories,
            SchemaVersionRepository schemaVersionRepository,
            AuthRepository authRepository,
            UserRepository userRepository,
            FeeWindowRepository feeWindowRepository,
            TransactionSizeRepository transactionSizeRepository,
            FirebaseInstallationIdRepository firebaseInstallationIdRepository,
            KeysRepository keysRepository,
            WalletClient walletClient
    ) {

        this.context = context;

        this.signupDraftManager = signupDraftManager;
        this.legacyLogout = legacyLogout;
        this.clearRepositories = clearRepositories;

        this.schemaVersionRepository = schemaVersionRepository;
        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.feeWindowRepository = feeWindowRepository;
        this.transactionSizeRepository = transactionSizeRepository;
        this.firebaseInstallationIdRepository = firebaseInstallationIdRepository;
        this.keysRepository = keysRepository;
        this.walletClient = walletClient;
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
        clearRepositories.clearAll();
    }

    private void logout() {
        legacyLogout.run();
    }

    /**
     * Destroys information for SignupDraft.
     */
    private void clearSignupDraft() {
        signupDraftManager.legacyClear();
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
                Collections.singletonMap(1, (double) prefs.getLong(feeFeeInSatoshisPerByte, 0)),
                1,  // The only fee target known after migration
                1,  // The only fee target known after migration
                1   // The only fee target known after migration
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
        transactionSizeRepository.initExpectedDebt();
    }

    private void moveUserToJsonPreference() {
        userRepository.migrateCthulhuToJsonPreference();
    }

    private void initDynamicFeeTargets() {
        feeWindowRepository.initDynamicFeeTargets();
    }

    private void initNtsOutpoints() {
        transactionSizeRepository.initNtsOutpoints();
    }

    private void moveFcmTokenToOwnRepository() {
        final SharedPreferences userRepositoryPrefs = context
                .getSharedPreferences("user", Context.MODE_PRIVATE);

        final boolean hasFcmToken = userRepositoryPrefs.contains("fcm_token_key");

        if (hasFcmToken) {

            final String fcmToken = userRepositoryPrefs.getString("fcm_token_key", null);
            firebaseInstallationIdRepository.storeFcmToken(fcmToken);

            userRepositoryPrefs.edit().remove("fcm_token_key").apply();
        }
    }

    private void addChallengePublicKeyVersion() {

        // RC ChallengePublicKey migrations ALWAYS needs to happen BEFORE Password
        // ChallengePublicKey migration. A change in ANY one of our ChallengePublicKeys results in
        // us re-encrypting the our base private key with our RC ChallengePublicKey (so if it hasn't
        // been migrated first, problem may arise).
        // Note: we shouldn't re-encrypt our basePrivateKey on a Password ChallengePublicKey change.
        if (keysRepository.hasChallengePublicKey(ChallengeType.RECOVERY_CODE)) {
            keysRepository.addChallengePublicKeyVersionMigration(ChallengeType.RECOVERY_CODE);
        }

        if (keysRepository.hasChallengePublicKey(ChallengeType.PASSWORD)) {
            keysRepository.addChallengePublicKeyVersionMigration(ChallengeType.PASSWORD);
        }
    }

    private void moveSignupDraftToSecureStorage() {
        signupDraftManager.moveSignupDraftToSecureStorage();
    }

    /**
     * Move the old, single-value Emergency Kit verification code preference to the "most recent"
     * list format.
     */
    private void migrateToRecentEmergencyKitVerificationCodes() {
        final SharedPreferences keysRepositoryPrefs = context
                .getSharedPreferences("keys", Context.MODE_PRIVATE);

        final String storedCode = keysRepositoryPrefs.getString("ek_activation_code", null);

        final StoredEkVerificationCodes storedCodes;

        if (TextUtils.isEmpty(storedCode)) {
            storedCodes = new StoredEkVerificationCodes();
        } else {
            storedCodes = new StoredEkVerificationCodes(Collections.singletonList(storedCode));
        }

        new JsonPreferenceAdapter<>(StoredEkVerificationCodes.class)
                .set("ek_recent_verification_codes", storedCodes, keysRepositoryPrefs.edit());
    }

    private void addEmergencyKitVersion() {
        userRepository.initEmergencyKitVersion();

        // Let's also move EK verification codes here too, shall we? We like related things to be
        // in one same place.

        final SharedPreferences keysRepositoryPrefs = context
                .getSharedPreferences("keys", Context.MODE_PRIVATE);

        final boolean hasRecentCodes = keysRepositoryPrefs.contains("ek_recent_verification_codes");

        if (hasRecentCodes) {

            final StoredEkVerificationCodes storedCodes =
                    new JsonPreferenceAdapter<>(StoredEkVerificationCodes.class)
                            .get("ek_recent_verification_codes", keysRepositoryPrefs);

            final List<String> fromOldestToNewest = storedCodes.getFromNewestToOldest();
            Collections.reverse(fromOldestToNewest);

            for (String ekCode : fromOldestToNewest) {
                userRepository.storeEmergencyKitVerificationCode(ekCode);
            }

            keysRepositoryPrefs.edit().remove("ek_recent_verification_codes").apply();
        }
    }

    private void initNtsUtxoStatus() {
        transactionSizeRepository.initNtsUtxoStatus();
    }

    private void moveNightModeToLibwalletStorage() {
        final SharedPreferences prefs = context
                .getSharedPreferences("current_night_mode", Context.MODE_PRIVATE);

        final String currentNightMode = prefs
                .getString("current_night_mode", NightMode.FOLLOW_SYSTEM.name());

        try {
            final NightMode nightMode = NightMode.valueOf(currentNightMode);
            walletClient.saveEnum("nightMode", nightMode);
        } catch (IllegalArgumentException e) {
            walletClient.saveEnum("nightMode", NightMode.FOLLOW_SYSTEM);
        }

        prefs.edit().remove("current_night_mode").apply();
    }

}
