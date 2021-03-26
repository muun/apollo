package io.muun.apollo.data.preferences;

import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter;
import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.apollo.domain.model.FeeWindow;

import android.content.Context;
import org.threeten.bp.ZonedDateTime;
import rx.Observable;

import java.util.SortedMap;
import javax.inject.Inject;

public class FeeWindowRepository extends BaseRepository {

    /**
     * Like FeeWindow, but JSON-serializable.
     */
    private static class StoredFeeWindow {
        // WAIT
        // WARNING
        // CAREFUL
        // READ THIS, I MEAN IT:

        // We forgot to exclude this class from Proguard rules. This means that the order of
        // declaration of this attributes is important -- until we remove this class from proguard
        // and migrate the preference to a non-minified JSON this class is APPEND-ONLY.

        public Long houstonId;
        public ZonedDateTime fetchDate;
        public SortedMap<Integer, Double> targetedFees;
        public int fastConfTarget;
        public int mediumConfTarget;
        public int slowConfTarget;

        public StoredFeeWindow() {
        }

        StoredFeeWindow(FeeWindow feeWindow) {
            this.houstonId = feeWindow.houstonId;
            this.fetchDate = feeWindow.fetchDate;
            this.targetedFees = feeWindow.targetedFees;
            this.fastConfTarget = feeWindow.fastConfTarget;
            this.mediumConfTarget = feeWindow.mediumConfTarget;
            this.slowConfTarget = feeWindow.slowConfTarget;
        }

        FeeWindow toFeeWindow() {
            return new FeeWindow(
                    houstonId,
                    fetchDate,
                    targetedFees,
                    fastConfTarget,
                    mediumConfTarget,
                    slowConfTarget
            );
        }
    }

    private static final String KEY_FEE_WINDOW = "fee_window";

    private final Preference<StoredFeeWindow> feeWindowPreference;

    /**
     * Constructor.
     */
    @Inject
    public FeeWindowRepository(Context context, RepositoryRegistry repositoryRegistry) {
        super(context, repositoryRegistry);

        feeWindowPreference = rxSharedPreferences.getObject(
                KEY_FEE_WINDOW,
                new JsonPreferenceAdapter<>(StoredFeeWindow.class)
        );
    }

    @Override
    protected String getFileName() {
        return "fee_window";
    }

    /**
     * Return true if `fetch()` is safe to call.
     */
    public boolean isSet() {
        return feeWindowPreference.isSet();
    }

    /**
     * Fetch an observable instance of the latest expected fee.
     */
    public Observable<FeeWindow> fetch() {
        return feeWindowPreference.asObservable().map(storedFeeWindow -> {
            if (storedFeeWindow != null) {
                return storedFeeWindow.toFeeWindow();
            } else {
                return null;
            }
        });
    }

    public FeeWindow fetchOne() {
        return fetch().toBlocking().first();
    }

    /**
     * Store the current expected fee.
     */
    public void store(FeeWindow feeWindow) {
        feeWindowPreference.set(new StoredFeeWindow(feeWindow));
    }

    /**
     * Migration to init dynamic fee targets.
     */
    public void initDynamicFeeTargets() {
        final FeeWindow feeWindow = fetchOne();

        if (feeWindow != null) {
            final FeeWindow migratedFeeWindow = feeWindow.initDynamicFeeTargets();
            store(migratedFeeWindow);
        }
    }
}
