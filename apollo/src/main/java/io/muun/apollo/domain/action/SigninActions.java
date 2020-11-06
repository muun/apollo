package io.muun.apollo.domain.action;

import io.muun.apollo.data.logging.LoggingContext;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.model.User;
import io.muun.common.Optional;
import io.muun.common.model.SessionStatus;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class SigninActions {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;

    /**
     * Constructor.
     */
    @Inject
    public SigninActions(AuthRepository authRepository, UserRepository userRepository) {

        this.authRepository = authRepository;
        this.userRepository = userRepository;
    }

    /**
     * Setups Crashlytics identifiers.
     */
    public void setupCrashlytics() {
        final User user = userRepository.fetchOne();

        if (user.email.isPresent()) {
            LoggingContext.configure(user.email.get(), user.hid.toString());
        }
    }

    public Optional<SessionStatus> getSessionStatus() {
        return authRepository.getSessionStatus();
    }

    public void clearSession() {
        authRepository.clear();
    }

    public void reportAuthorizedByEmail() {
        authRepository.storeSessionStatus(SessionStatus.AUTHORIZED_BY_EMAIL);
    }
}
