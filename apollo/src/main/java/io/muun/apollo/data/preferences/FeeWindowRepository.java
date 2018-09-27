package io.muun.apollo.data.preferences;

import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.model.FeeWindow;

import android.content.Context;
import com.f2prateek.rx.preferences.Preference;
import rx.Observable;

import javax.inject.Inject;

public class FeeWindowRepository extends BaseRepository {

    public static final String KEY_HOUSTON_ID = "houston_id";
    public static final String KEY_FETCH_DATE = "fetch_date";
    public static final String KEY_FEE_IN_SATOSHIS_PER_BYTE = "fee_in_satoshis_per_byte";

    private final Preference<Long> houstonIdPreference;
    private final Preference<String> fetchDatePreference;
    private final Preference<Long> feeInSatoshisPerByte;

    /**
     * Constructor.
     */
    @Inject
    public FeeWindowRepository(Context context) {

        super(context);

        houstonIdPreference = rxSharedPreferences.getLong(KEY_HOUSTON_ID);
        fetchDatePreference = rxSharedPreferences.getString(KEY_FETCH_DATE);
        feeInSatoshisPerByte = rxSharedPreferences.getLong(KEY_FEE_IN_SATOSHIS_PER_BYTE);
    }

    @Override
    protected String getFileName() {
        return "fee_window";
    }

    /**
     * Return true if `fetch()` is safe to call.
     */
    public boolean isSet() {
        return houstonIdPreference.isSet()
                && fetchDatePreference.isSet()
                && feeInSatoshisPerByte.isSet();
    }

    /**
     * Fetch an observable instance of the latest expected fee.
     */
    public Observable<FeeWindow> fetch() {

        return Observable.combineLatest(
                houstonIdPreference.asObservable(),
                fetchDatePreference.asObservable().map(SerializationUtils::deserializeDate),
                feeInSatoshisPerByte.asObservable(),
                FeeWindow::new
        );
    }

    public FeeWindow fetchOne() {
        return fetch().toBlocking().first();
    }

    /**
     * Store the current expected fee.
     */
    public void store(FeeWindow feeWindow) {

        houstonIdPreference.set(feeWindow.houstonId);
        fetchDatePreference.set(SerializationUtils.serializeDate(feeWindow.fetchDate));
        feeInSatoshisPerByte.set(feeWindow.feeInSatoshisPerByte);
    }
}
