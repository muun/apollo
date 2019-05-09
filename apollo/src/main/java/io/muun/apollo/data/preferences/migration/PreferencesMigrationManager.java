package io.muun.apollo.data.preferences.migration;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.SchemaVersionRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.LogoutActions;

import javax.inject.Inject;

/**
 * Migrates the shared preferences, running all the not-already-run migrations, storing in a shared
 * preference the index of the last one run.
 */
public class PreferencesMigrationManager {

    private final SchemaVersionRepository schemaVersionRepository;
    private final AuthRepository authRepository;

    private final LogoutActions logoutActions;

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

            this::clearSignupDraft,
            this::logout
    };

    /**
     * Creates migration manager.
     */
    @Inject
    public PreferencesMigrationManager(AuthRepository authRepository,
                                       UserRepository userRepository,
                                       SchemaVersionRepository schemaVersionRepository,
                                       LogoutActions logoutActions) {

        this.schemaVersionRepository = schemaVersionRepository;

        this.userRepository = userRepository;
        this.authRepository = authRepository;

        this.logoutActions = logoutActions;
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

    private void clearAllRepositories() {
        logoutActions.clearAllRepositories();
    }

    private void logout() {
        logoutActions.logout();
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
}
