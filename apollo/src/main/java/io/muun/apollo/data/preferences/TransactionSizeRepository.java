package io.muun.apollo.data.preferences;


import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.model.NextTransactionSize;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import com.f2prateek.rx.preferences.Preference;
import rx.Observable;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class TransactionSizeRepository extends BaseRepository {

    private static final String KEY_TRANSACTION_SIZE = "transaction_size";

    private final Preference<String> transactionSizePreference;

    @Inject
    public TransactionSizeRepository(Context context) {
        super(context);
        transactionSizePreference = rxSharedPreferences.getString(KEY_TRANSACTION_SIZE, null);
    }

    @Override
    protected String getFileName() {
        return "transaction_size";
    }

    /**
     * Get the stored NextTransactionSize.
     */
    @Nullable
    public NextTransactionSize getNextTransactionSize() {
        return watchNextTransactionSize().toBlocking().first();
    }

    /**
     * Watch the stored NextTransactionSize.
     */
    public Observable<NextTransactionSize> watchNextTransactionSize() {
        return transactionSizePreference.asObservable()
                .map(json -> {
                    if (json != null) {
                        return SerializationUtils.deserializeJson(NextTransactionSize.class, json);
                    } else {
                        return null;
                    }
                });
    }

    /**
     * Replace the stored NextTransactionSize.
     */
    public void setTransactionSize(NextTransactionSize transactionSize) {
        transactionSizePreference.set(
                SerializationUtils.serializeJson(NextTransactionSize.class, transactionSize)
        );
    }

    /**
     * Migration to init expected debt for pre-existing NTSs.
     */
    public void initExpectedDebt() {
        final NextTransactionSize nts = getNextTransactionSize();

        Preconditions.checkNotNull(nts);

        setTransactionSize(nts.initExpectedDebt());
    }
}
