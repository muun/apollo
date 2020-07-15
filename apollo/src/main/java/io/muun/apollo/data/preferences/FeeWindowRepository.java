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
        public Long houstonId;
        public ZonedDateTime fetchDate;
        public SortedMap<Integer, Double> targetedFees;

        public StoredFeeWindow() {
        }

        StoredFeeWindow(FeeWindow feeWindow) {
            this.houstonId = feeWindow.houstonId;
            this.fetchDate = feeWindow.fetchDate;
            this.targetedFees = feeWindow.targetedFees;
        }

        FeeWindow toFeeWindow() {
            return new FeeWindow(houstonId, fetchDate, targetedFees);
        }
    }

    private static final String KEY_FEE_WINDOW = "fee_window";

    private final Preference<StoredFeeWindow> feeWindowPreference;

    /**
     * Constructor.
     */
    @Inject
    public FeeWindowRepository(Context context) {
        super(context);

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
        return feeWindowPreference.asObservable().map(StoredFeeWindow::toFeeWindow);
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
}
