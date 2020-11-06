package io.muun.apollo.data.preferences;

import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.common.Optional;
import io.muun.common.model.SessionStatus;
import io.muun.common.utils.Encodings;

import android.content.Context;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public class AuthRepository extends BaseRepository {

    private static final String KEY_SERVER_JWT = "server_jwt";

    private static final String KEY_SESSION_STATUS = "session_status";

    private final Preference<SessionStatus> sessionStatusPreference;

    private final SecureStorageProvider secureStorageProvider;

    /**
     * Creates a repository for auth data.
     */
    @Inject
    public AuthRepository(Context context, SecureStorageProvider secureStorageProvider) {
        super(context);

        this.secureStorageProvider = secureStorageProvider;
        this.sessionStatusPreference = rxSharedPreferences.getEnum(
                KEY_SESSION_STATUS,
                null,
                SessionStatus.class
        );
    }

    @Override
    protected String getFileName() {
        return "auth";
    }

    /**
     * Returns the server jwt.
     */
    @NotNull
    public Optional<String> getServerJwt() {
        if (!secureStorageProvider.has(KEY_SERVER_JWT)) {
            return Optional.empty();
        }

        final byte[] serverJwt = secureStorageProvider.get(KEY_SERVER_JWT);

        return Optional.ofNullable(Encodings.bytesToString(serverJwt));
    }

    /**
     * Store server JWT on secure storage.
     */
    public void storeServerJwt(@NotNull String serverJwt) {
        this.secureStorageProvider.put(KEY_SERVER_JWT, Encodings.stringToBytes(serverJwt));
    }

    public void storeSessionStatus(SessionStatus sessionStatus) {
        sessionStatusPreference.set(sessionStatus);
    }

    @Override
    public void clear() {
        super.clear();
        secureStorageProvider.delete(KEY_SERVER_JWT);
    }

    /**
     * Returns the session status.
     */
    public Optional<SessionStatus> getSessionStatus() {
        return watchSessionStatus().toBlocking().first();
    }

    public Observable<Optional<SessionStatus>> watchSessionStatus() {
        return sessionStatusPreference.asObservable()
                .map(Optional::ofNullable);
    }

    /**
     * One-time method utility for preference migration.
     */
    public void moveJwtToSecureStorage() {
        final Preference<String> serverJwtPrefs = rxSharedPreferences.getString(KEY_SERVER_JWT);
        if (serverJwtPrefs.isSet()) {
            final String serverJwt = serverJwtPrefs.get();
            storeServerJwt(serverJwt);
            serverJwtPrefs.delete();
        }
    }
}
