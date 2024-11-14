package io.muun.apollo.domain.action;

import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.common.Optional;
import io.muun.common.model.SessionStatus;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class SigninActions {

    private final AuthRepository authRepository;

    /**
     * Constructor.
     */
    @Inject
    public SigninActions(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public Optional<SessionStatus> getSessionStatus() {
        return authRepository.getSessionStatus();
    }

    /**
     * Delete session/auth related data currently stored, both in preferences as in secure storage.
     */
    public void clearSession() {
        authRepository.clearSession();
    }

    /**
     * Update session status to AUTHORIZED_BY_EMAIL.
     */
    public void reportAuthorizedByEmail() {
        authRepository.storeSessionStatus(SessionStatus.AUTHORIZED_BY_EMAIL);
    }
}
