package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter;
import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.domain.model.PendingWithdrawal;
import io.muun.apollo.domain.satellite.messages.SatelliteStateMessage;
import io.muun.apollo.domain.satellite.states.SatelliteEmptyState;
import io.muun.common.Optional;

import android.content.Context;
import rx.Observable;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public class SatelliteStateRepository extends BaseRepository {

    private static final String KEY_PENDING_WITHDRAWAL = "pending_withdrawal";
    private static final String KEY_SATELLITE_STATE = "satellite_state";

    private final Preference<PendingWithdrawal> pendingWithdrawalPreference;
    private final Preference<SatelliteStateMessage> satelliteStatePreference;

    /**
     * Constructor.
     */
    @Inject
    public SatelliteStateRepository(Context context) {
        super(context);

        pendingWithdrawalPreference = rxSharedPreferences.getObject(
                KEY_PENDING_WITHDRAWAL,
                new JsonPreferenceAdapter<>(PendingWithdrawal.class)
        );

        satelliteStatePreference = rxSharedPreferences.getObject(
                KEY_SATELLITE_STATE,
                new JsonPreferenceAdapter<>(SatelliteStateMessage.class)
        );

        if (! satelliteStatePreference.isSet()) {
            // By doing this instead of using a SatelliteState object as default, we ensure that
            // the initial value is of the same type (LinkedHashMap) as later values.
            satelliteStatePreference.set(new SatelliteEmptyState().getStateMessage());
        }
    }

    @Override
    protected String getFileName() {
        return "pending_withdrawal";
    }

    // Pending withdrawal:
    public Optional<PendingWithdrawal> getPendingWithdrawal() {
        return Optional.ofNullable(pendingWithdrawalPreference.get());
    }

    public void setPendingWithdrawal(@Nullable PendingWithdrawal pendingWithdrawal) {
        pendingWithdrawalPreference.set(pendingWithdrawal);
    }

    public Observable<Optional<PendingWithdrawal>> watchPendingWithdrawal() {
        return pendingWithdrawalPreference.asObservable().map(Optional::ofNullable);
    }

    // Satellite state:
    @NotNull
    public SatelliteStateMessage getSatelliteState() {
        return satelliteStatePreference.get();
    }

    public void setSatelliteState(SatelliteStateMessage satelliteState) {
        satelliteStatePreference.set(satelliteState);
    }
}
